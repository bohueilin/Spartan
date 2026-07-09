import Foundation

/// The seam Spartan depends on for wearable data. Implementations:
///  - `MockWhoopClient`  — clearly-labeled sample data; the default when no credentials exist.
///  - `RealWhoopClient`  — Phase-2 OAuth2 + REST against the WHOOP Developer API.
///
/// Returning normalized `WhoopSnapshot`s (not raw WHOOP DTOs) keeps everything above this
/// boundary wearable-agnostic, so additional wearables can be added as new WhoopClient-style
/// adapters without touching the coaching engine or UI.
///
/// Synchronous for now: the only implementation is the in-memory mock. Async wrappers arrive
/// with real networking in Phase 2.
public protocol WhoopClient {
    /// Whether this client is serving mock/sample data (surfaced in the UI for honesty).
    var isMock: Bool { get }

    /// Most recent `days` of data, oldest first, today last. May be empty if unavailable.
    func fetchRecentDays(days: Int) -> [WhoopSnapshot]
}

public extension WhoopClient {
    /// Convenience mirroring the Android default argument (`days = 7`).
    func fetchRecentDays() -> [WhoopSnapshot] {
        fetchRecentDays(days: 7)
    }
}
