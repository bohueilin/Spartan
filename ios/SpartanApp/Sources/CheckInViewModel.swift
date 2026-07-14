// CheckInViewModel.swift — today's plan, persistence, and actions for the check-in screen.
//
// Faithful port of the Android MainViewModel daily check-in slice
// (app/src/main/java/com/spartan/ui/screens/MainViewModel.kt) + DailyPlanSync:
//   LocalFirstWhoopClient (imported CSV data first, sample data fallback) ->
//   ReadinessSnapshot.from -> CoachingEngine.buildPlan,
//   seed-once persistence (user state is never overwritten by a regenerate; a WHOOP CSV
//   import force-reseeds pending items while keeping completed ones),
//   complete / uncomplete / snooze(60m) / skip / schedule actions, and
//   quiet-hours-aware local notifications (22:00–07:00, mirroring ReminderEngine.isQuietHours).
//
// Privacy: both JSON stores hold health-derived data, so writes use complete file
// protection and the store directory is excluded from iCloud backup (guideline 5.1.3)
// via SpartanSecureFile — see WhoopImportStore.swift.
//
// Honest status: source-complete; awaits an Xcode compile pass (no iOS SDK on the
// authoring machine). Epoch days, epoch minutes, and epoch millis are `Int` throughout,
// matching SpartanKit's actual value types (Int is 64-bit on all supported devices).

import Foundation
import UserNotifications
import SpartanKit

// MARK: - PlanStore (JSON persistence, Application Support)

/// Persists each day's `[DailyActivity]` keyed by dateEpochDay as JSON in
/// Application Support — the iOS analogue of the Android Room `daily_activities` table.
/// Seed-once semantics (HealthRepository.seedDailyPlanIfNeeded): if a day already has
/// activities, a regenerated plan never overwrites them, so user check-offs survive
/// app relaunches and plan rebuilds.
public final class PlanStore {
    private struct State: Codable {
        var days: [String: [DailyActivity]] = [:]
    }

    private var state = State()
    private let fileURL: URL

    public init(directory: URL? = nil) {
        let dir = directory ?? PlanStore.defaultDirectory()
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        SpartanSecureFile.excludeFromBackup(dir)
        fileURL = dir.appendingPathComponent("plan-store.json")
        if let data = try? Data(contentsOf: fileURL),
           let loaded = try? JSONDecoder().decode(State.self, from: data) {
            state = loaded
        }
    }

    public func activities(forDay day: Int) -> [DailyActivity] {
        state.days[String(day)] ?? []
    }

    /// Seed-once: only writes when the day has no activities yet.
    public func seedIfNeeded(day: Int, activities: [DailyActivity]) {
        guard (state.days[String(day)] ?? []).isEmpty else { return }
        state.days[String(day)] = activities
        persist()
    }

    /// Force-reseed after the data source changed (CSV import, disconnect): completed
    /// activities are kept, everything pending is replaced by the fresh plan
    /// (HealthRepository.reseedDailyPlan — delete non-completed + insert-if-absent).
    public func reseed(day: Int, activities: [DailyActivity]) {
        let kept = (state.days[String(day)] ?? []).filter { $0.status == .done }
        let keptIds = Set(kept.map { $0.id })
        state.days[String(day)] = kept + activities.filter { !keptIds.contains($0.id) }
        persist()
    }

    /// Replaces the stored list for a day (used after a status update).
    public func replace(day: Int, activities: [DailyActivity]) {
        state.days[String(day)] = activities
        persist()
    }

    public func eraseAll() {
        state = State()
        try? FileManager.default.removeItem(at: fileURL)
    }

    private func persist() {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        if let data = try? encoder.encode(state) {
            SpartanSecureFile.writeProtected(data, to: fileURL)
        }
    }

    static func defaultDirectory() -> URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? FileManager.default.temporaryDirectory
        return base.appendingPathComponent("Spartan", isDirectory: true)
    }
}

// MARK: - SettingsStore (JSON settings, no UserDefaults)

/// Small PlanStore-adjacent JSON settings file: onboarding + integration consent state.
/// Mirrors the Android PreferencesStore + integration_connections rows, local-only.
public struct SpartanSettings: Codable, Equatable {
    public var onboardingComplete: Bool = false
    public var displayName: String = ""
    public var heightCm: Double?
    public var whoopConnected: Bool = false
    public var calendarConnected: Bool = false

    public init() {}
}

public final class SettingsStore {
    private let fileURL: URL
    public private(set) var settings = SpartanSettings()

    public init(directory: URL? = nil) {
        let dir = directory ?? PlanStore.defaultDirectory()
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        SpartanSecureFile.excludeFromBackup(dir)
        fileURL = dir.appendingPathComponent("settings.json")
        if let data = try? Data(contentsOf: fileURL),
           let loaded = try? JSONDecoder().decode(SpartanSettings.self, from: data) {
            settings = loaded
        }
    }

    public func update(_ mutate: (inout SpartanSettings) -> Void) {
        mutate(&settings)
        if let data = try? JSONEncoder().encode(settings) {
            SpartanSecureFile.writeProtected(data, to: fileURL)
        }
    }

    public func eraseAll() {
        settings = SpartanSettings()
        try? FileManager.default.removeItem(at: fileURL)
    }
}

// MARK: - WHOOP CSV import state (Android WhoopImportUiState / WhoopCsvImporter.Summary)

/// What one WHOOP CSV import produced, rendered on the Connections screen.
public struct WhoopImportSummary: Equatable {
    public let days: Int
    public let firstDayEpoch: Int
    public let lastDayEpoch: Int
    public let workouts: Int
    public let journalDays: Int
    public let recognizedFiles: [String]
    public let skippedFiles: [String]
}

/// Progress + outcome of a WHOOP CSV import (nil when none this session).
public struct WhoopImportUiState: Equatable {
    public var inProgress: Bool = false
    public var summary: WhoopImportSummary? = nil
    public var failed: Bool = false
    /// Files that WERE recognized when the import still failed (e.g. journal-only pick).
    public var failedButRecognized: [String] = []
}

// MARK: - CheckInViewModel

@MainActor
public final class CheckInViewModel: ObservableObject {
    // Today's plan state (MainUiState check-in slice).
    @Published public private(set) var activities: [DailyActivity] = []
    @Published public private(set) var planHeadline: String = ""
    @Published public private(set) var readinessBand: ReadinessBand?
    @Published public private(set) var recoveryScore: Int?
    @Published public private(set) var planSafetyBanner: String?
    @Published public private(set) var whoopIsMock: Bool = true
    @Published public private(set) var syncFailed: Bool = false

    // Consent / onboarding state.
    @Published public private(set) var whoopConnected: Bool = false
    @Published public private(set) var calendarConnected: Bool = false
    @Published public private(set) var onboardingComplete: Bool = false

    // WHOOP CSV import state (Connections screen result card).
    @Published public private(set) var whoopImport: WhoopImportUiState?

    private let planStore: PlanStore
    private let settingsStore: SettingsStore
    private let whoopImportStore: WhoopImportStore
    /// Imported CSV data first, labeled sample data as the fallback — the same seam
    /// DailyPlanSync uses on Android, so a CSV import changes the data without
    /// touching the sync path.
    private let whoopClient: WhoopClient
    private let coachingEngine = CoachingEngine()
    private let calendarClient = StubCalendarClient()
    private let availabilityService = AvailabilityService()

    /// Latest WHOOP snapshot, kept for the sleep-anchored scheduling window.
    private var latestSnapshot: WhoopSnapshot?
    private var today: Int = CheckInViewModel.localEpochDay()

    public init(
        planStore: PlanStore = PlanStore(),
        settingsStore: SettingsStore = SettingsStore(),
        whoopImportStore: WhoopImportStore = WhoopImportStore()
    ) {
        self.planStore = planStore
        self.settingsStore = settingsStore
        self.whoopImportStore = whoopImportStore
        self.whoopClient = LocalFirstWhoopClient(delegate: MockWhoopClient(), store: whoopImportStore)
        let s = settingsStore.settings
        onboardingComplete = s.onboardingComplete
        whoopConnected = s.whoopConnected
        calendarConnected = s.calendarConnected
        loadToday()
    }

    /// LocalDate.now().toEpochDay() equivalent: calendar days since 1970-01-01 in local time.
    static func localEpochDay(_ date: Date = Date(), calendar: Calendar = .current) -> Int {
        let offset = TimeInterval(calendar.timeZone.secondsFromGMT(for: date))
        return Int(((date.timeIntervalSince1970 + offset) / 86_400).rounded(.down))
    }

    private static func nowMillis() -> Int {
        Int(Date().timeIntervalSince1970 * 1000)
    }

    // MARK: Daily sync (DailyPlanSync.sync equivalent)

    /// Pull WHOOP data (imported CSV data when any exists, sample data otherwise), build
    /// today's plan, and seed the store — never overwriting user state on a regenerate.
    /// `forceReseed` (after an import or disconnect) replaces the day's not-yet-completed
    /// activities instead of keeping them, so the plan reflects the new data source.
    /// A failed/empty fetch just sets `syncFailed`; it never wipes the last-known plan.
    public func loadToday(forceReseed: Bool = false) {
        today = Self.localEpochDay()
        reactivateExpiredSnoozes()

        let snapshots = whoopClient.fetchRecentDays(days: 7)
        guard let todaySnapshot = snapshots.last else {
            syncFailed = true
            activities = planStore.activities(forDay: today)
            return
        }
        syncFailed = false
        latestSnapshot = todaySnapshot

        let readiness = ReadinessSnapshot.from(today: todaySnapshot, history: Array(snapshots.dropLast()))
        let plan = coachingEngine.buildPlan(readiness: readiness, options: CoachingOptions())

        if forceReseed {
            planStore.reseed(day: today, activities: plan.activities)
        } else {
            planStore.seedIfNeeded(day: today, activities: plan.activities)
        }

        activities = planStore.activities(forDay: today)
        planHeadline = plan.headline
        readinessBand = readiness.band
        recoveryScore = readiness.recoveryScore
        planSafetyBanner = plan.safetyBanner
        whoopIsMock = plan.isMock
    }

    /// Expired snoozes return to PLANNED so the plan never silently loses activities
    /// (HealthRepository.reactivateExpiredSnoozes).
    private func reactivateExpiredSnoozes() {
        let now = Self.nowMillis()
        var stored = planStore.activities(forDay: today)
        var changed = false
        for index in stored.indices {
            if stored[index].status == .snoozed,
               let until = stored[index].snoozedUntilMillis, until <= now {
                stored[index].status = .planned
                stored[index].snoozedUntilMillis = nil
                changed = true
            }
        }
        if changed { planStore.replace(day: today, activities: stored) }
    }

    // MARK: Actions

    public func complete(_ id: String) {
        updateActivityStatus(id: id, status: .done, completedAtMillis: Self.nowMillis())
    }

    public func uncomplete(_ id: String) {
        updateActivityStatus(id: id, status: .planned)
    }

    /// Snooze means "remind me later", so schedule the later (quiet hours permitting).
    public func snooze(_ id: String, minutes: Int = 60) {
        guard let activity = activities.first(where: { $0.id == id }) else { return }
        let wakeAtMillis = Self.nowMillis() + minutes * 60_000
        updateActivityStatus(id: id, status: .snoozed, snoozedUntilMillis: wakeAtMillis)
        scheduleActivityReminder(
            activityId: id,
            title: "Back on: \(activity.title)",
            body: "~\(activity.estimatedMinutes) min. Open Spartan for why this helps today.",
            triggerAtMillis: wakeAtMillis
        )
    }

    public func skip(_ id: String) {
        updateActivityStatus(id: id, status: .skipped)
    }

    /// Find the earliest calendar gap that fits the activity and reschedule it there.
    /// The window is anchored to the user's actual sleep pattern from WHOOP
    /// (wake + 30 min -> bed − 60 min) so a nudge never lands during sleep;
    /// static 08:00–21:00 is only the no-data fallback. Mirrors MainViewModel.scheduleActivity.
    public func schedule(_ id: String) {
        guard let activity = activities.first(where: { $0.id == id }) else { return }
        let snap = latestSnapshot
        let startMinuteOfDay = max(8 * 60, (snap?.wakeMinuteOfDay ?? (8 * 60 - 30)) + 30)
        let rawEnd = (snap?.bedMinuteOfDay ?? (22 * 60)) - 60
        let endMinuteOfDay = min(max(rawEnd, startMinuteOfDay + 30), 23 * 60)

        let dayStartEpochMinute = Int(Calendar.current.startOfDay(for: Date()).timeIntervalSince1970) / 60
        let start = dayStartEpochMinute + startMinuteOfDay
        let end = dayStartEpochMinute + endMinuteOfDay

        let busy = calendarClient.freeBusy(startEpochMinute: start, endEpochMinute: end)
        guard let slot = availabilityService.suggestSlot(
            activityMinutes: activity.estimatedMinutes,
            dayStart: start,
            dayEnd: end,
            busy: busy
        ) else { return }

        updateActivityStatus(id: id, status: .rescheduled, scheduledEpochMinute: slot.startEpochMinute)
        scheduleActivityReminder(
            activityId: id,
            title: "Time for: \(activity.title)",
            body: "~\(activity.estimatedMinutes) min. Open Spartan for why this helps today.",
            triggerAtMillis: slot.startEpochMinute * 60_000
        )
        if calendarConnected {
            _ = calendarClient.createEvent(
                title: activity.title,
                startEpochMinute: slot.startEpochMinute,
                durationMinutes: activity.estimatedMinutes
            )
        }
    }

    /// Mirrors HealthRepository.updateActivityStatus: the status columns are replaced,
    /// not merged, so an uncomplete clears completedAt and a re-plan clears snooze state.
    private func updateActivityStatus(
        id: String,
        status: ActivityStatus,
        completedAtMillis: Int? = nil,
        snoozedUntilMillis: Int? = nil,
        scheduledEpochMinute: Int? = nil
    ) {
        var updated = activities
        guard let index = updated.firstIndex(where: { $0.id == id }) else { return }
        updated[index].status = status
        updated[index].completedAtMillis = completedAtMillis
        updated[index].snoozedUntilMillis = snoozedUntilMillis
        updated[index].scheduledEpochMinute = scheduledEpochMinute
        activities = updated
        planStore.replace(day: today, activities: updated)
    }

    // MARK: WHOOP CSV import (MainViewModel.importWhoopCsv + WhoopCsvImporter.import)

    /// Applies one WHOOP CSV import: persist the merged records, then rebuild today's plan
    /// from the real data (completed check-ins are kept; pending sample-driven items are
    /// replaced). The Connections screen does the file reading/parsing (security-scoped
    /// URLs are a UI-layer concern) and hands the merged `WhoopImportData` here.
    public func applyWhoopImport(_ data: WhoopImportData, recognizedFiles: [String], skippedFiles: [String]) {
        if whoopImport?.inProgress == true { return } // one import at a time
        whoopImport = WhoopImportUiState(inProgress: true)

        // Nothing a plan can run on: journal-only picks are "recognized but unusable",
        // everything else is "not a WHOOP export" (WhoopCsvImporter.ImportError).
        guard !(data.cycles.isEmpty && data.workouts.isEmpty) else {
            whoopImport = WhoopImportUiState(failed: true, failedButRecognized: recognizedFiles)
            return
        }

        whoopImportStore.upsert(cycles: data.cycles, workouts: data.workouts)

        // A force-reseed replaces today's pending activities; any one-shot reminders armed
        // for their snoozed/rescheduled times must die with them.
        cancelPendingActivityReminders()
        loadToday(forceReseed: true)

        // Consent records only what actually arrived; a workouts-only import doesn't flip
        // the connection to CONNECTED because plans would still run on sample data.
        if !data.cycles.isEmpty {
            settingsStore.update { $0.whoopConnected = true }
            whoopConnected = true
        }

        let days = data.cycles.map { $0.dateEpochDay } + data.workouts.map { $0.dateEpochDay }
        whoopImport = WhoopImportUiState(summary: WhoopImportSummary(
            days: data.cycles.count,
            firstDayEpoch: days.min() ?? 0,
            lastDayEpoch: days.max() ?? 0,
            workouts: data.workouts.count,
            journalDays: data.cycles.filter {
                $0.journalCaffeine != nil || $0.journalAlcohol != nil || $0.journalLateMeal != nil
            }.count,
            recognizedFiles: recognizedFiles,
            skippedFiles: skippedFiles
        ))
    }

    public func dismissWhoopImportResult() {
        whoopImport = nil
    }

    /// One-shot reminders armed for snoozed/rescheduled activities are removed before a
    /// force-reseed, or they'd fire for items that no longer exist
    /// (MainViewModel.cancelPendingActivityReminders).
    private func cancelPendingActivityReminders() {
        let identifiers = planStore.activities(forDay: today)
            .filter { $0.status == .snoozed || $0.status == .rescheduled }
            .map { "activity-\($0.id)" }
        guard !identifiers.isEmpty else { return }
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: identifiers)
    }

    // MARK: Consent (Connections screen)

    public func connectWhoop() {
        settingsStore.update { $0.whoopConnected = true }
        whoopConnected = true
    }

    /// Disconnect stops the source: imported cycles/workouts are removed so the next sync
    /// falls back to labeled sample data (LocalFirstWhoopClient reverts to its delegate),
    /// and today's pending plan is rebuilt from that source. Mirrors
    /// MainViewModel.disconnectWhoop + HealthRepository.clearImportedWhoopSource.
    public func disconnectWhoop() {
        settingsStore.update { $0.whoopConnected = false }
        whoopConnected = false
        whoopImportStore.eraseAll()
        cancelPendingActivityReminders()
        loadToday(forceReseed: true)
    }

    public func connectCalendar() {
        settingsStore.update { $0.calendarConnected = true }
        calendarConnected = true
    }

    public func disconnectCalendar() {
        settingsStore.update { $0.calendarConnected = false }
        calendarConnected = false
    }

    // MARK: Onboarding + data deletion

    public func completeOnboarding(name: String, heightCm: Double?) {
        settingsStore.update {
            $0.displayName = name.isEmpty ? "You" : name
            $0.heightCm = heightCm
            $0.onboardingComplete = true
        }
        onboardingComplete = true
    }

    /// Deletes all locally stored data (plan history + settings + imported WHOOP data)
    /// and clears notifications — pending AND already delivered, so no health-plan
    /// reminder lingers in Notification Center after a delete-everything request.
    /// Mirrors MainViewModel.deleteAllLocalData.
    public func deleteAllData() {
        UNUserNotificationCenter.current().removeAllPendingNotificationRequests()
        UNUserNotificationCenter.current().removeAllDeliveredNotifications()
        planStore.eraseAll()
        settingsStore.eraseAll()
        whoopImportStore.eraseAll()
        whoopImport = nil
        whoopConnected = false
        calendarConnected = false
        loadToday()
    }

    // MARK: Local notifications (quiet-hours aware)

    /// Quiet-hours window, same defaults and wrap-past-midnight logic as
    /// ReminderEngine.isQuietHours on Android (22:00 -> 07:00).
    static let defaultQuietStartMinuteOfDay = 22 * 60
    static let defaultQuietEndMinuteOfDay = 7 * 60

    static func isQuietHours(
        minuteOfDay: Int,
        quietStartMinuteOfDay: Int = defaultQuietStartMinuteOfDay,
        quietEndMinuteOfDay: Int = defaultQuietEndMinuteOfDay
    ) -> Bool {
        if quietStartMinuteOfDay == quietEndMinuteOfDay { return false }
        if quietStartMinuteOfDay < quietEndMinuteOfDay {
            return minuteOfDay >= quietStartMinuteOfDay && minuteOfDay < quietEndMinuteOfDay
        }
        return minuteOfDay >= quietStartMinuteOfDay || minuteOfDay < quietEndMinuteOfDay
    }

    /// Notification category whose `hiddenPreviewsBodyPlaceholder` keeps redacted
    /// lock-screen previews (Show Previews = When Unlocked/Never) to a neutral
    /// "You have a reminder" instead of just the app name. Bodies themselves carry no
    /// health-state text either, so Show Previews = Always is equally safe.
    private static let reminderCategoryId = "spartan.reminder"
    private static let registerReminderCategory: Void = {
        let category = UNNotificationCategory(
            identifier: reminderCategoryId,
            actions: [],
            intentIdentifiers: [],
            hiddenPreviewsBodyPlaceholder: "You have a reminder",
            options: []
        )
        UNUserNotificationCenter.current().setNotificationCategories([category])
    }()

    /// One-shot local notification for an activity at `triggerAtMillis`. Skipped if the
    /// time has passed or falls in quiet hours; authorization is requested lazily on
    /// first use. Replaces any prior reminder for the same activity so re-scheduling
    /// never spams (ReminderScheduler.scheduleActivityReminder). The body is
    /// deliberately generic — the whyItMatters rationale stays inside the app, one tap
    /// away, and never on a lock screen.
    private func scheduleActivityReminder(activityId: String, title: String, body: String, triggerAtMillis: Int) {
        guard triggerAtMillis > Self.nowMillis() else { return }
        let triggerDate = Date(timeIntervalSince1970: TimeInterval(triggerAtMillis) / 1000)
        let components = Calendar.current.dateComponents([.hour, .minute], from: triggerDate)
        let minuteOfDay = (components.hour ?? 0) * 60 + (components.minute ?? 0)
        guard !Self.isQuietHours(minuteOfDay: minuteOfDay) else { return }

        _ = Self.registerReminderCategory
        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound]) { granted, _ in
            guard granted else { return }
            let content = UNMutableNotificationContent()
            content.title = title
            content.body = body
            content.categoryIdentifier = Self.reminderCategoryId
            content.sound = .default
            let interval = max(1, triggerDate.timeIntervalSinceNow)
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: interval, repeats: false)
            let identifier = "activity-\(activityId)"
            center.removePendingNotificationRequests(withIdentifiers: [identifier])
            center.add(UNNotificationRequest(identifier: identifier, content: content, trigger: trigger))
        }
    }
}
