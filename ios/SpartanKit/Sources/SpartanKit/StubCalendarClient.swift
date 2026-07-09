import Foundation

/// The seam Spartan depends on for calendar availability. Implementations:
///  - `StubCalendarClient`    — mock free/busy; the default when no credentials exist.
///  - GoogleCalendarClient    — Phase-2, backed by the Google Calendar API.
///
/// Least privilege: reads use free/busy only (never event contents). Event creation is a separate,
/// strictly opt-in capability.
///
/// Synchronous for now: the only implementation is the in-memory stub. Async wrappers arrive
/// with real networking in Phase 2.
public protocol CalendarClient {
    var isStub: Bool { get }

    /// Busy blocks overlapping `[startEpochMinute, endEpochMinute)`. Free/busy only — no event details.
    func freeBusy(startEpochMinute: Int, endEpochMinute: Int) -> [TimeWindow]

    /// Create a calendar event for a scheduled activity. Opt-in; requires the write scope.
    /// Returns the created event id.
    func createEvent(title: String, startEpochMinute: Int, durationMinutes: Int) -> String
}

/// SAMPLE DATA ONLY. Returns a plausible set of busy blocks for today so the scheduling flow works
/// with no Google credentials and no network. Reads are free/busy-shaped (opaque busy ranges, never
/// event titles or details), mirroring the least-privilege contract of the real client.
public final class StubCalendarClient: CalendarClient {

    public let isStub: Bool = true

    public init() {}

    public func freeBusy(startEpochMinute: Int, endEpochMinute: Int) -> [TimeWindow] {
        // Anchor on local midnight today and add wall-clock offsets. Simplification vs the Android
        // original (which resolves each time through the zone rules): on a DST-transition day the
        // blocks can shift by the changed hour. Acceptable for clearly-labeled sample data.
        let startOfToday = Calendar.current.startOfDay(for: Date())
        let midnightEpochMinute = Int(startOfToday.timeIntervalSince1970 / 60)
        func blockAt(_ startH: Int, _ startM: Int, _ endH: Int, _ endM: Int) -> TimeWindow {
            TimeWindow(
                startEpochMinute: midnightEpochMinute + startH * 60 + startM,
                endEpochMinute: midnightEpochMinute + endH * 60 + endM
            )
        }
        let busy = [
            blockAt(9, 0, 10, 0),    // stand-up + focus
            blockAt(12, 30, 13, 0),  // lunch call
            blockAt(14, 0, 15, 30),  // meetings
            blockAt(17, 0, 17, 30),  // wrap-up
        ]
        return busy.filter { $0.endEpochMinute > startEpochMinute && $0.startEpochMinute < endEpochMinute }
    }

    public func createEvent(title: String, startEpochMinute: Int, durationMinutes: Int) -> String {
        // No-op in stub mode: report success with a synthetic id so the confirm-flow can be tested.
        return "stub-event-\(startEpochMinute)"
    }
}
