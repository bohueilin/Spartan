// CheckInViewModel.swift — today's plan, persistence, and actions for the check-in screen.
//
// Faithful port of the Android MainViewModel daily check-in slice
// (app/src/main/java/com/spartan/ui/screens/MainViewModel.kt) + DailyPlanSync:
//   MockWhoopClient -> ReadinessSnapshot.from -> CoachingEngine.buildPlan,
//   seed-once persistence (user state is never overwritten by a regenerate),
//   complete / uncomplete / snooze(60m) / skip / schedule actions, and
//   quiet-hours-aware local notifications (22:00–07:00, mirroring ReminderEngine.isQuietHours).
//
// Honest status: source-complete; awaits an Xcode compile pass (no iOS SDK on the
// authoring machine). SpartanKit value types are assumed to mirror the Kotlin domain
// 1:1 (Int64 for Kotlin Long, var properties on structs).

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
        fileURL = dir.appendingPathComponent("plan-store.json")
        if let data = try? Data(contentsOf: fileURL),
           let loaded = try? JSONDecoder().decode(State.self, from: data) {
            state = loaded
        }
    }

    public func activities(forDay day: Int64) -> [DailyActivity] {
        state.days[String(day)] ?? []
    }

    /// Seed-once: only writes when the day has no activities yet.
    public func seedIfNeeded(day: Int64, activities: [DailyActivity]) {
        guard (state.days[String(day)] ?? []).isEmpty else { return }
        state.days[String(day)] = activities
        persist()
    }

    /// Replaces the stored list for a day (used after a status update).
    public func replace(day: Int64, activities: [DailyActivity]) {
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
            try? data.write(to: fileURL, options: .atomic)
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
        fileURL = dir.appendingPathComponent("settings.json")
        if let data = try? Data(contentsOf: fileURL),
           let loaded = try? JSONDecoder().decode(SpartanSettings.self, from: data) {
            settings = loaded
        }
    }

    public func update(_ mutate: (inout SpartanSettings) -> Void) {
        mutate(&settings)
        if let data = try? JSONEncoder().encode(settings) {
            try? data.write(to: fileURL, options: .atomic)
        }
    }

    public func eraseAll() {
        settings = SpartanSettings()
        try? FileManager.default.removeItem(at: fileURL)
    }
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

    private let planStore: PlanStore
    private let settingsStore: SettingsStore
    private let whoopClient = MockWhoopClient()
    private let coachingEngine = CoachingEngine()
    private let calendarClient = StubCalendarClient()
    private let availabilityService = AvailabilityService()

    /// Latest WHOOP snapshot, kept for the sleep-anchored scheduling window.
    private var latestSnapshot: WhoopSnapshot?
    private var today: Int64 = CheckInViewModel.localEpochDay()

    public init(planStore: PlanStore = PlanStore(), settingsStore: SettingsStore = SettingsStore()) {
        self.planStore = planStore
        self.settingsStore = settingsStore
        let s = settingsStore.settings
        onboardingComplete = s.onboardingComplete
        whoopConnected = s.whoopConnected
        calendarConnected = s.calendarConnected
        loadToday()
    }

    /// LocalDate.now().toEpochDay() equivalent: calendar days since 1970-01-01 in local time.
    static func localEpochDay(_ date: Date = Date(), calendar: Calendar = .current) -> Int64 {
        let offset = TimeInterval(calendar.timeZone.secondsFromGMT(for: date))
        return Int64(((date.timeIntervalSince1970 + offset) / 86_400).rounded(.down))
    }

    private static func nowMillis() -> Int64 {
        Int64(Date().timeIntervalSince1970 * 1000)
    }

    // MARK: Daily sync (DailyPlanSync.sync equivalent)

    /// Pull sample WHOOP data, build today's plan, and seed the store — never overwriting
    /// user state on regenerate. A failed/empty fetch just sets `syncFailed`; it never
    /// wipes the last-known plan.
    public func loadToday() {
        today = Self.localEpochDay()
        reactivateExpiredSnoozes()

        let snapshots = (try? whoopClient.fetchRecentDays(days: 7)) ?? []
        guard let todaySnapshot = snapshots.last else {
            syncFailed = true
            activities = planStore.activities(forDay: today)
            return
        }
        syncFailed = false
        latestSnapshot = todaySnapshot

        let readiness = ReadinessSnapshot.from(today: todaySnapshot, history: Array(snapshots.dropLast()))
        let plan = coachingEngine.buildPlan(readiness: readiness, options: CoachingOptions())

        planStore.seedIfNeeded(day: today, activities: plan.activities)

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
        let wakeAtMillis = Self.nowMillis() + Int64(minutes) * 60_000
        updateActivityStatus(id: id, status: .snoozed, snoozedUntilMillis: wakeAtMillis)
        scheduleActivityReminder(
            activityId: id,
            title: "Back on: \(activity.title)",
            body: "~\(activity.estimatedMinutes) min. \(activity.whyItMatters)",
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

        let dayStartEpochMinute = Int64(Calendar.current.startOfDay(for: Date()).timeIntervalSince1970) / 60
        let start = dayStartEpochMinute + Int64(startMinuteOfDay)
        let end = dayStartEpochMinute + Int64(endMinuteOfDay)

        let busy = (try? calendarClient.freeBusy(startEpochMinute: start, endEpochMinute: end)) ?? []
        guard let slot = availabilityService.suggestSlot(
            activityMinutes: activity.estimatedMinutes,
            dayStartEpochMinute: start,
            dayEndEpochMinute: end,
            busy: busy
        ) else { return }

        updateActivityStatus(id: id, status: .rescheduled, scheduledEpochMinute: slot.startEpochMinute)
        scheduleActivityReminder(
            activityId: id,
            title: "Time for: \(activity.title)",
            body: "~\(activity.estimatedMinutes) min. \(activity.whyItMatters)",
            triggerAtMillis: slot.startEpochMinute * 60_000
        )
        if calendarConnected {
            _ = try? calendarClient.createEvent(
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
        completedAtMillis: Int64? = nil,
        snoozedUntilMillis: Int64? = nil,
        scheduledEpochMinute: Int64? = nil
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

    // MARK: Consent (Connections screen)

    public func connectWhoop() {
        settingsStore.update { $0.whoopConnected = true }
        whoopConnected = true
    }

    public func disconnectWhoop() {
        settingsStore.update { $0.whoopConnected = false }
        whoopConnected = false
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

    /// Deletes all locally stored data (plan history + settings) and cancels pending
    /// notifications. Mirrors MainViewModel.deleteAllLocalData.
    public func deleteAllData() {
        UNUserNotificationCenter.current().removeAllPendingNotificationRequests()
        planStore.eraseAll()
        settingsStore.eraseAll()
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

    /// One-shot local notification for an activity at `triggerAtMillis`. Skipped if the
    /// time has passed or falls in quiet hours; authorization is requested lazily on
    /// first use. Replaces any prior reminder for the same activity so re-scheduling
    /// never spams (ReminderScheduler.scheduleActivityReminder).
    private func scheduleActivityReminder(activityId: String, title: String, body: String, triggerAtMillis: Int64) {
        guard triggerAtMillis > Self.nowMillis() else { return }
        let triggerDate = Date(timeIntervalSince1970: TimeInterval(triggerAtMillis) / 1000)
        let components = Calendar.current.dateComponents([.hour, .minute], from: triggerDate)
        let minuteOfDay = (components.hour ?? 0) * 60 + (components.minute ?? 0)
        guard !Self.isQuietHours(minuteOfDay: minuteOfDay) else { return }

        let center = UNUserNotificationCenter.current()
        center.requestAuthorization(options: [.alert, .sound]) { granted, _ in
            guard granted else { return }
            let content = UNMutableNotificationContent()
            content.title = title
            content.body = body
            content.sound = .default
            let interval = max(1, triggerDate.timeIntervalSinceNow)
            let trigger = UNTimeIntervalNotificationTrigger(timeInterval: interval, repeats: false)
            let identifier = "activity-\(activityId)"
            center.removePendingNotificationRequests(withIdentifiers: [identifier])
            center.add(UNNotificationRequest(identifier: identifier, content: content, trigger: trigger))
        }
    }
}
