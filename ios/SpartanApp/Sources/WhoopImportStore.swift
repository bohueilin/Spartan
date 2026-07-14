// WhoopImportStore.swift — persistence for imported WHOOP CSV data + the local-first client.
//
// iOS analogue of the Android `whoop_cycles` / `whoop_workouts` Room tables and
// `com.spartan.data.whoop.LocalFirstWhoopClient`: the merged records from a WHOOP CSV
// export land here as JSON, and `LocalFirstWhoopClient` serves them to the sync path in
// place of sample data. Disconnecting WHOOP erases this store, which reverts the client
// to its delegate (the labeled sample source) on the next sync.
//
// Privacy: this file holds real health data, so writes use complete file protection and
// the store is excluded from iCloud backup (health data must never reach iCloud backup —
// App Review guideline 5.1.3). Same posture as Android, where the Room DB is excluded
// from cloud backup.
//
// Honest status: source-complete; awaits an Xcode compile pass (no iOS SDK on the
// authoring machine).

import Foundation
import SpartanKit

// MARK: - Secure file writes (health data stays on-device)

/// One place for the on-disk privacy rules every Spartan store follows:
///  - `.completeFileProtection`: encrypted at rest, inaccessible while the device is locked.
///  - `isExcludedFromBackup`: never copied into iCloud/iTunes backups (guideline 5.1.3 —
///    health data must not reach iCloud).
enum SpartanSecureFile {
    /// Atomic write with complete file protection, then backup exclusion on the result.
    /// (Atomic replace creates a fresh file, so the exclusion is re-applied after every write.)
    static func writeProtected(_ data: Data, to url: URL) {
        try? data.write(to: url, options: [.atomic, .completeFileProtection])
        excludeFromBackup(url)
    }

    /// Marks a file or directory as excluded from backup. On a directory this covers
    /// everything inside it, so stores also apply it to their containing directory.
    static func excludeFromBackup(_ url: URL) {
        var target = url
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try? target.setResourceValues(values)
    }
}

// MARK: - WhoopImportStore (JSON persistence, Application Support)

/// Persists the cycles/workouts produced by `WhoopCsvMerger.merge` as JSON in Application
/// Support. Upsert semantics match the Android Room tables: cycles replace on
/// `dateEpochDay`, workouts on `id`, and days from earlier imports are kept.
public final class WhoopImportStore {
    private struct State: Codable {
        var cycles: [WhoopCycleRecord] = []
        var workouts: [WhoopWorkoutRecord] = []
    }

    private var state = State()
    private let fileURL: URL

    public init(directory: URL? = nil) {
        let dir = directory ?? PlanStore.defaultDirectory()
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        SpartanSecureFile.excludeFromBackup(dir)
        fileURL = dir.appendingPathComponent("whoop-import.json")
        if let data = try? Data(contentsOf: fileURL),
           let loaded = try? JSONDecoder().decode(State.self, from: data) {
            state = loaded
        }
    }

    /// True when at least one imported cycle exists (the signal that plans run on real data).
    public var hasImportedCycles: Bool { !state.cycles.isEmpty }

    /// The most recent `count` merged cycle records, oldest first
    /// (Android `WhoopCycleDao.latestCycles` + the client-side ascending sort).
    public func latestCycles(_ count: Int) -> [WhoopCycleRecord] {
        Array(state.cycles.sorted { $0.dateEpochDay < $1.dateEpochDay }.suffix(max(count, 1)))
    }

    /// Upserts one import's output: new records win on their key, other days are kept
    /// (Android `upsertCycles` / `upsertWorkouts` REPLACE semantics).
    public func upsert(cycles: [WhoopCycleRecord], workouts: [WhoopWorkoutRecord]) {
        var cyclesByDay = Dictionary(
            state.cycles.map { ($0.dateEpochDay, $0) },
            uniquingKeysWith: { _, new in new }
        )
        for cycle in cycles { cyclesByDay[cycle.dateEpochDay] = cycle }
        state.cycles = cyclesByDay.values.sorted { $0.dateEpochDay < $1.dateEpochDay }

        var workoutsById = Dictionary(
            state.workouts.map { ($0.id, $0) },
            uniquingKeysWith: { _, new in new }
        )
        for workout in workouts { workoutsById[workout.id] = workout }
        state.workouts = workoutsById.values.sorted {
            ($0.dateEpochDay, $0.startMinuteOfDay ?? 0) < ($1.dateEpochDay, $1.startMinuteOfDay ?? 0)
        }
        persist()
    }

    /// Removes all imported data (WHOOP disconnect, and Privacy → delete all).
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
}

// MARK: - LocalFirstWhoopClient

/// Serves the user's imported WHOOP data when any exists, and falls back to `delegate`
/// (the mock, or the real OAuth client when configured) otherwise. This is what turns a
/// CSV import into "the app now runs on my real data" without touching the sync path:
/// `CheckInViewModel.loadToday` keeps calling the same `WhoopClient` seam.
///
/// Faithful port of Android's `LocalFirstWhoopClient`, including the isMock contract:
/// real imported data is never labeled sample.
public final class LocalFirstWhoopClient: WhoopClient {
    private let delegate: WhoopClient
    private let store: WhoopImportStore

    /// Reflects the source of the LAST fetch; until the first fetch this reports the
    /// delegate's value — acceptable because the view model syncs on launch and the UI's
    /// primary flag rides on the plan's own isMock.
    private var lastServedImported = false

    public init(delegate: WhoopClient, store: WhoopImportStore) {
        self.delegate = delegate
        self.store = store
    }

    public var isMock: Bool { lastServedImported ? false : delegate.isMock }

    public func fetchRecentDays(days: Int) -> [WhoopSnapshot] {
        let imported = store.latestCycles(max(days, 1))
        lastServedImported = !imported.isEmpty
        guard !imported.isEmpty else { return delegate.fetchRecentDays(days: days) }
        return imported.map { $0.toSnapshot() }
    }
}
