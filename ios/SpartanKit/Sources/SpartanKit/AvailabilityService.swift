import Foundation

/// Finds open time windows that fit an activity, honoring working hours, a sleep window, quiet
/// hours, and existing busy blocks. The core algorithm is pure and works entirely in epoch minutes
/// (UTC), so timezone/DST handling is a caller concern (the caller derives the day/working bounds
/// from local time) and the logic stays deterministic and unit-testable.
public struct AvailabilityService {

    public init() {}

    /// Open windows within `[dayStart, dayEnd)` (epoch minutes) after removing `busy` blocks.
    /// `busy` blocks may overlap and be unsorted. Returned windows are sorted, non-overlapping,
    /// and at least `minWindowMinutes` long.
    public func openWindows(
        dayStart: Int,
        dayEnd: Int,
        busy: [TimeWindow],
        minWindowMinutes: Int = 5
    ) -> [TimeWindow] {
        guard dayEnd > dayStart else { return [] }
        let merged = mergeBusy(busy.compactMap { clamp($0, lo: dayStart, hi: dayEnd) })
        var open: [TimeWindow] = []
        var cursor = dayStart
        for block in merged {
            if block.startEpochMinute > cursor {
                open.append(TimeWindow(startEpochMinute: cursor, endEpochMinute: block.startEpochMinute))
            }
            cursor = max(cursor, block.endEpochMinute)
        }
        if cursor < dayEnd {
            open.append(TimeWindow(startEpochMinute: cursor, endEpochMinute: dayEnd))
        }
        return open.filter { $0.durationMinutes >= minWindowMinutes }
    }

    /// The earliest open window that can fit `activityMinutes`, returned trimmed to exactly that
    /// length starting at the window's start. Nil if nothing fits.
    public func suggestSlot(
        activityMinutes: Int,
        dayStart: Int,
        dayEnd: Int,
        busy: [TimeWindow]
    ) -> TimeWindow? {
        guard let fit = openWindows(dayStart: dayStart, dayEnd: dayEnd, busy: busy, minWindowMinutes: activityMinutes)
            .first(where: { $0.durationMinutes >= activityMinutes }) else { return nil }
        return TimeWindow(startEpochMinute: fit.startEpochMinute, endEpochMinute: fit.startEpochMinute + activityMinutes)
    }

    private func clamp(_ w: TimeWindow, lo: Int, hi: Int) -> TimeWindow? {
        let s = max(w.startEpochMinute, lo)
        let e = min(w.endEpochMinute, hi)
        return e > s ? TimeWindow(startEpochMinute: s, endEpochMinute: e) : nil
    }

    private func mergeBusy(_ blocks: [TimeWindow]) -> [TimeWindow] {
        guard !blocks.isEmpty else { return [] }
        let sorted = blocks.sorted { $0.startEpochMinute < $1.startEpochMinute }
        var out: [TimeWindow] = [sorted[0]]
        for b in sorted.dropFirst() {
            let last = out[out.count - 1]
            if b.startEpochMinute <= last.endEpochMinute {
                out[out.count - 1] = TimeWindow(
                    startEpochMinute: last.startEpochMinute,
                    endEpochMinute: max(last.endEpochMinute, b.endEpochMinute)
                )
            } else {
                out.append(b)
            }
        }
        return out
    }
}
