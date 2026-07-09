import Foundation

/// SAMPLE DATA ONLY. Produces a plausible 7-day WHOOP-style series so Spartan can be developed,
/// demoed, and tested with no credentials and no network. Every snapshot is tagged `isMock = true`
/// and the UI surfaces a "Sample data" label. This is NOT real WHOOP data and must never be
/// presented as if it were.
///
/// The series ends on an easy-recovery day with mild sleep debt and a slightly elevated resting
/// heart rate, so the coaching engine has meaningful signals to demonstrate.
public final class MockWhoopClient: WhoopClient {

    public let isMock: Bool = true

    public init() {}

    public func fetchRecentDays(days: Int) -> [WhoopSnapshot] {
        // Days since 1970-01-01 computed in UTC. Simplification vs the Android original
        // (LocalDate.now().toEpochDay(), which uses the local calendar date): within a few hours
        // of local midnight the two can differ by one day. Acceptable for clearly-labeled sample
        // data; the real client will carry proper local dates.
        let today = Int(Date().timeIntervalSince1970 / 86_400)
        // recovery, hrv, rhr, sleepPerf, sleepHrs, sleepDebtHrs, resp, strain, kcal
        let series: [Sample] = [
            Sample(recovery: 72, hrv: 78.0, rhr: 52.0, sleepPerf: 88, sleepHours: 7.6, sleepDebt: 0.2, resp: 14.2, strain: 12.4, kcal: 2450.0),
            Sample(recovery: 64, hrv: 71.0, rhr: 53.0, sleepPerf: 82, sleepHours: 7.1, sleepDebt: 0.6, resp: 14.0, strain: 15.1, kcal: 2610.0),
            Sample(recovery: 58, hrv: 66.0, rhr: 54.0, sleepPerf: 76, sleepHours: 6.8, sleepDebt: 1.0, resp: 14.3, strain: 16.8, kcal: 2740.0),
            Sample(recovery: 49, hrv: 61.0, rhr: 55.0, sleepPerf: 71, sleepHours: 6.5, sleepDebt: 1.4, resp: 14.6, strain: 17.9, kcal: 2810.0),
            Sample(recovery: 55, hrv: 63.0, rhr: 55.0, sleepPerf: 74, sleepHours: 6.9, sleepDebt: 1.1, resp: 14.4, strain: 11.2, kcal: 2380.0),
            Sample(recovery: 61, hrv: 68.0, rhr: 54.0, sleepPerf: 80, sleepHours: 7.2, sleepDebt: 0.7, resp: 14.1, strain: 13.0, kcal: 2500.0),
            Sample(recovery: 42, hrv: 58.0, rhr: 59.0, sleepPerf: 63, sleepHours: 6.1, sleepDebt: 1.8, resp: 15.1, strain: 9.4, kcal: 2210.0), // today
        ]
        let clamped = max(1, min(days, series.count))
        let window = Array(series.suffix(clamped))
        let startDay = today - (window.count - 1)
        return window.enumerated().map { index, s in
            WhoopSnapshot(
                dateEpochDay: startDay + index,
                recoveryScore: s.recovery,
                hrvMs: s.hrv,
                restingHeartRate: s.rhr,
                sleepPerformance: s.sleepPerf,
                sleepDurationHours: s.sleepHours,
                sleepDebtHours: s.sleepDebt,
                respiratoryRate: s.resp,
                dayStrain: s.strain,
                energyKcal: s.kcal,
                bedMinuteOfDay: 22 * 60 + 45, // sample bedtime 22:45
                wakeMinuteOfDay: 6 * 60 + 30, // sample wake 06:30
                isMock: true
            )
        }
    }

    private struct Sample {
        let recovery: Int
        let hrv: Double
        let rhr: Double
        let sleepPerf: Int
        let sleepHours: Double
        let sleepDebt: Double
        let resp: Double
        let strain: Double
        let kcal: Double
    }
}
