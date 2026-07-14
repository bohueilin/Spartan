// CheckInView.swift — the Spartan daily check-in, the hero screen.
//
// Visual-parity port of the Android check-in (app/src/main/java/com/spartan/ui/screens/
// CheckInScreen.kt): one calm column, a single accent, instant hierarchy, tactile
// check-off, and honest loading/empty states. All user-facing copy is copied verbatim
// from app/src/main/res/values/strings.xml. Interactive elements meet the 48pt touch
// target minimum and carry VoiceOver semantics; no fixed text frame heights, so Dynamic
// Type can reflow freely.
//
// Honest status: source-complete; awaits an Xcode compile pass (no iOS SDK on the
// authoring machine).

import SwiftUI
import SpartanKit

struct CheckInView: View {
    @EnvironmentObject private var viewModel: CheckInViewModel

    /// Switches the root TabView to the Connections tab (Android's onManageConnections):
    /// the connect prompt routes there instead of flipping any state itself, so
    /// connecting/importing always happens on the screen with the full consent copy.
    let onManageConnections: () -> Void

    // Skeleton only while a first sync is genuinely in flight — a failed sync falls
    // through to the empty state + banner instead of looping the skeleton forever.
    private var loading: Bool {
        viewModel.planHeadline.isEmpty && viewModel.activities.isEmpty &&
            viewModel.readinessBand == nil && !viewModel.syncFailed
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: SpartanSpacing.md) {
                Spacer().frame(height: SpartanSpacing.lg)
                ReadinessHeader()

                if loading {
                    LoadingPlan()
                } else {
                    PlanProgress()
                    if viewModel.syncFailed {
                        SafetyBanner(text: "Couldn't refresh WHOOP data. Showing your most recent sync.")
                    }
                    if let banner = viewModel.planSafetyBanner {
                        SafetyBanner(text: banner)
                    }
                    if !viewModel.whoopConnected {
                        ConnectPrompt(isMock: viewModel.whoopIsMock, onManageConnections: onManageConnections)
                    }
                    SectionLabel(text: "TODAY'S PLAN")
                    if viewModel.activities.isEmpty {
                        EmptyPlan()
                    } else {
                        // Priority first, then the natural order of the day (morning -> evening).
                        ForEach(orderedActivities) { activity in
                            ActivityCard(
                                activity: activity,
                                onComplete: { viewModel.complete($0) },
                                onUncomplete: { viewModel.uncomplete($0) },
                                onSnooze: { viewModel.snooze($0) },
                                onSkip: { viewModel.skip($0) },
                                onSchedule: { viewModel.schedule($0) }
                            )
                        }
                    }
                }
                FooterDisclaimer()
            }
            .padding(.horizontal, SpartanSpacing.xl)
        }
        .background(Color.spartanBackground.ignoresSafeArea())
        .onAppear { viewModel.loadToday() }
    }

    private var orderedActivities: [DailyActivity] {
        viewModel.activities.sorted { a, b in
            let pa = ordinal(a.priority), pb = ordinal(b.priority)
            if pa != pb { return pa < pb }
            return ordinal(a.bestTimeOfDay) < ordinal(b.bestTimeOfDay)
        }
    }
}

// Declaration-order ordinals, matching the Kotlin enum ordering used for sorting.
private func ordinal(_ priority: ActivityPriority) -> Int {
    ActivityPriority.allCases.firstIndex(of: priority) ?? 0
}

private func ordinal(_ time: TimeOfDay) -> Int {
    TimeOfDay.allCases.firstIndex(of: time) ?? 0
}

// MARK: - Header (wordmark + readiness ring card)

private struct ReadinessHeader: View {
    @EnvironmentObject private var viewModel: CheckInViewModel

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("SPARTAN")
                    .font(.footnote.weight(.bold))
                    .kerning(3)
                    .foregroundColor(.spartanAccent)
                Spacer()
                if viewModel.whoopIsMock { SampleDataChip() }
            }
            Spacer().frame(height: SpartanSpacing.lg)
            HStack(alignment: .center, spacing: SpartanSpacing.xl) {
                ReadinessRing(
                    recovery: viewModel.recoveryScore,
                    band: viewModel.readinessBand
                )
                VStack(alignment: .leading, spacing: SpartanSpacing.xs) {
                    Text(bandName)
                        .font(.headline.weight(.bold))
                        .foregroundColor(spartanBandColor(viewModel.readinessBand))
                    Text(viewModel.planHeadline.isEmpty ? "Building today's plan..." : viewModel.planHeadline)
                        .font(.subheadline)
                        .foregroundColor(.spartanOnSurface)
                        .fixedSize(horizontal: false, vertical: true)
                }
                Spacer(minLength: 0)
            }
            .padding(SpartanSpacing.xl)
            .background(
                RoundedRectangle(cornerRadius: SpartanRadius.card)
                    .fill(Color.spartanSurface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: SpartanRadius.card)
                    .strokeBorder(Color.spartanOutline, lineWidth: 1)
            )
        }
    }

    private var bandName: String {
        viewModel.readinessBand.map(spartanBandLabel) ?? "Readiness"
    }
}

private struct ReadinessRing: View {
    let recovery: Int?
    let band: ReadinessBand?

    var body: some View {
        let color = spartanBandColor(band)
        let sweep = Double(min(max(recovery ?? 0, 0), 100)) / 100.0
        ZStack {
            Circle()
                .stroke(Color.spartanSurfaceVariant, style: StrokeStyle(lineWidth: 9, lineCap: .round))
            Circle()
                .trim(from: 0, to: CGFloat(sweep))
                .stroke(color, style: StrokeStyle(lineWidth: 9, lineCap: .round))
                .rotationEffect(.degrees(-90))
            VStack(spacing: 0) {
                Text(recovery.map(String.init) ?? "--")
                    .font(.title2.weight(.bold))
                    .foregroundColor(.spartanOnSurface)
                Text("recovery")
                    .font(.caption2)
                    .foregroundColor(.spartanOnSurfaceVariant)
            }
        }
        .frame(width: 88, height: 88)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(a11yLabel)
    }

    // "Recovery %1$s, %2$s" with "%1$d percent" / "not available".
    private var a11yLabel: String {
        let recoveryText = recovery.map { "\($0) percent" } ?? "not available"
        let bandName = band.map(spartanBandLabel) ?? "Readiness"
        return "Recovery \(recoveryText), \(bandName)"
    }
}

// MARK: - Progress

private struct PlanProgress: View {
    @EnvironmentObject private var viewModel: CheckInViewModel

    var body: some View {
        let total = viewModel.activities.count
        let done = viewModel.activities.filter { $0.status == .done }.count
        let minutes = viewModel.activities
            .filter { $0.status != .skipped }
            .reduce(0) { $0 + $1.estimatedMinutes }
        VStack(alignment: .leading, spacing: SpartanSpacing.sm) {
            HStack {
                Text("\(done) of \(total) done")
                    .font(.footnote.weight(.semibold))
                    .foregroundColor(.spartanOnSurface)
                Spacer()
                Text("~\(minutes) min today")
                    .font(.footnote)
                    .foregroundColor(.spartanOnSurfaceVariant)
            }
            ProgressView(value: total == 0 ? 0 : Double(done) / Double(total))
                .progressViewStyle(.linear)
                .tint(.spartanAccent)
                .animation(.easeInOut(duration: 0.22), value: done)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(done) of \(total) activities done")
    }
}

// MARK: - Activity card

private struct ActivityCard: View {
    let activity: DailyActivity
    let onComplete: (String) -> Void
    let onUncomplete: (String) -> Void
    let onSnooze: (String) -> Void
    let onSkip: (String) -> Void
    let onSchedule: (String) -> Void

    @State private var expanded = false

    private var done: Bool { activity.status == .done }
    private var dimmed: Bool { done || activity.status == .skipped }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top, spacing: SpartanSpacing.xs) {
                SpartanCheck(
                    done: done,
                    label: activity.title,
                    onToggle: { done ? onUncomplete(activity.id) : onComplete(activity.id) }
                )
                VStack(alignment: .leading, spacing: SpartanSpacing.xs) {
                    Text(activity.title)
                        .font(.headline.weight(.semibold))
                        .strikethrough(dimmed)
                        .foregroundColor(dimmed ? .spartanOnSurfaceVariant : .spartanOnSurface)
                        .fixedSize(horizontal: false, vertical: true)
                    HStack(spacing: SpartanSpacing.sm) {
                        PriorityChip(priority: activity.priority)
                        Text("~\(activity.estimatedMinutes) min · \(timeOfDayLabel(activity.bestTimeOfDay))")
                            .font(.caption)
                            .foregroundColor(.spartanOnSurfaceVariant)
                    }
                    statusLine
                }
                .padding(.top, 6)
                .contentShape(Rectangle())
                .onTapGesture { withAnimation(.easeInOut(duration: 0.22)) { expanded.toggle() } }
                .accessibilityAddTraits(.isButton)
                .accessibilityHint(expanded ? "Collapse \(activity.title)" : "Expand \(activity.title)")
                Spacer(minLength: 0)
                Menu {
                    Button("Snooze 1 hour") { onSnooze(activity.id) }
                    Button("Skip today") { onSkip(activity.id) }
                    Button("Find a time") { onSchedule(activity.id) }
                } label: {
                    Image(systemName: "ellipsis")
                        .foregroundColor(.spartanOnSurfaceVariant)
                        .frame(width: 48, height: 48)
                        .contentShape(Rectangle())
                }
                .accessibilityLabel("More options for \(activity.title)")
            }
            if expanded { expandedDetail }
        }
        .padding(SpartanSpacing.md)
        .background(
            RoundedRectangle(cornerRadius: SpartanRadius.card)
                .fill(Color.spartanSurface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: SpartanRadius.card)
                .strokeBorder(
                    activity.priority == .required ? Color.spartanAccent.opacity(0.28) : Color.spartanOutline,
                    lineWidth: 1
                )
        )
        .contextMenu {
            Button("Snooze 1 hour") { onSnooze(activity.id) }
            Button("Skip today") { onSkip(activity.id) }
            Button("Find a time") { onSchedule(activity.id) }
        }
    }

    @ViewBuilder
    private var statusLine: some View {
        if let text = statusText {
            Text(text)
                .font(.caption2)
                .foregroundColor(.spartanAccent)
                .padding(.top, SpartanSpacing.xs)
        }
    }

    private var statusText: String? {
        switch activity.status {
        case .snoozed:
            if let until = activity.snoozedUntilMillis {
                return "Snoozed until \(clockTime(fromMillis: until))"
            }
            return "Snoozed"
        case .skipped:
            return "Skipped for today"
        case .rescheduled:
            if let minute = activity.scheduledEpochMinute {
                return "Scheduled for \(clockTime(fromMillis: minute * 60_000))"
            }
            return "Rescheduled"
        case .done:
            return "Completed"
        default:
            return nil
        }
    }

    private var expandedDetail: some View {
        VStack(alignment: .leading, spacing: 0) {
            Spacer().frame(height: SpartanSpacing.md)
            DetailLabel(text: "WHY THIS MATTERS")
            Text(activity.whyItMatters)
                .font(.subheadline)
                .foregroundColor(.spartanOnSurface)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 2)
            if !activity.instructions.isEmpty {
                Spacer().frame(height: SpartanSpacing.md)
                DetailLabel(text: "STEPS")
                ForEach(Array(activity.instructions.enumerated()), id: \.offset) { index, step in
                    HStack(alignment: .top, spacing: 0) {
                        Text("\(index + 1). ")
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(.spartanAccent)
                        Text(step)
                            .font(.subheadline)
                            .foregroundColor(.spartanOnSurface)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                    .padding(.top, 3)
                }
            }
            if let note = activity.safetyNote {
                Spacer().frame(height: SpartanSpacing.sm)
                Text(note)
                    .font(.caption)
                    .foregroundColor(.spartanTertiary)
                    .fixedSize(horizontal: false, vertical: true)
            }
            // Follow-along video for this activity, when the library has one. Opening is
            // always user-initiated and leaves the app (checkin_follow_along_video copy:
            // "Follow along: %1$s (%2$d min)").
            if let guide = VideoLibrary.guideForActivity(activityId: activity.id),
               let guideURL = URL(string: guide.url) {
                Link(destination: guideURL) {
                    HStack(spacing: SpartanSpacing.sm) {
                        Image(systemName: "play.circle")
                        Text("Follow along: \(guide.title) (\(guide.minutes) min)")
                            .lineLimit(1)
                            .truncationMode(.tail)
                    }
                    .frame(minHeight: 48)
                }
                .font(.subheadline)
                .foregroundColor(.spartanAccent)
                .padding(.top, SpartanSpacing.xs)
                .accessibilityLabel("Follow along: \(guide.title), \(guide.minutes) minutes")
            }
            Button {
                onSchedule(activity.id)
            } label: {
                HStack(spacing: SpartanSpacing.sm) {
                    Image(systemName: "calendar")
                    Text("Find a time")
                }
                .frame(minHeight: 48)
            }
            .foregroundColor(.spartanAccent)
            .padding(.top, SpartanSpacing.xs)
        }
    }
}

/// Tactile 26pt rounded check inside a 48pt touch target, with checkbox semantics.
private struct SpartanCheck: View {
    let done: Bool
    let label: String
    let onToggle: () -> Void

    var body: some View {
        Button(action: onToggle) {
            ZStack {
                RoundedRectangle(cornerRadius: 9)
                    .fill(done ? Color.spartanAccent : Color.clear)
                RoundedRectangle(cornerRadius: 9)
                    .strokeBorder(done ? Color.spartanAccent : Color.spartanOnSurfaceVariant, lineWidth: 2)
                Image(systemName: "checkmark")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(.spartanOnAccent)
                    .opacity(done ? 1 : 0)
            }
            .frame(width: 26, height: 26)
            .frame(width: 48, height: 48)
            .contentShape(Rectangle())
            .animation(.easeInOut(duration: 0.14), value: done)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(label)
        .accessibilityValue(done ? "Completed" : "Not completed")
        .accessibilityAddTraits(done ? [.isSelected] : [])
    }
}

private struct PriorityChip: View {
    let priority: ActivityPriority

    var body: some View {
        let (label, color) = chipStyle
        Text(label)
            .font(.caption2.weight(.bold))
            .foregroundColor(color)
            .padding(.horizontal, SpartanSpacing.sm)
            .padding(.vertical, 3)
            .background(
                RoundedRectangle(cornerRadius: SpartanRadius.chip)
                    .fill(color.opacity(0.14))
            )
    }

    private var chipStyle: (String, Color) {
        switch priority {
        case .required: return ("REQUIRED", .spartanAccent)
        case .recommended: return ("RECOMMENDED", .spartanSecondary)
        case .optional: return ("OPTIONAL", .spartanOnSurfaceVariant)
        }
    }
}

// MARK: - Chips, banners, empty/loading states

private struct SampleDataChip: View {
    var body: some View {
        Text("SAMPLE DATA")
            .font(.caption2.weight(.bold))
            .foregroundColor(.spartanTertiary)
            .padding(.horizontal, SpartanSpacing.sm)
            .padding(.vertical, 3)
            .background(
                RoundedRectangle(cornerRadius: SpartanRadius.chip)
                    .fill(Color.spartanTertiary.opacity(0.16))
            )
    }
}

private struct SafetyBanner: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.caption)
            .foregroundColor(.spartanOnSurfaceVariant)
            .fixedSize(horizontal: false, vertical: true)
            .padding(SpartanSpacing.md)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: SpartanRadius.card)
                    .fill(Color.spartanSurfaceVariant)
            )
    }
}

/// Routes to the Connections tab rather than acting itself (Android CheckInScreen
/// ConnectPrompt parity): no real-data promise is made here — the copy points at the
/// CSV import, the honest real-data path this build actually ships.
private struct ConnectPrompt: View {
    let isMock: Bool
    let onManageConnections: () -> Void

    var body: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: SpartanSpacing.xs) {
                Text("Connect WHOOP")
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(.spartanOnSurface)
                Text(isMock
                    ? "You're viewing sample data. Import your WHOOP export (.csv) in Connections to run on your real recovery, sleep, and strain."
                    : "Connect WHOOP to personalize your plan.")
                    .font(.caption)
                    .foregroundColor(.spartanOnSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer()
            Button("Connect") { onManageConnections() }
                .font(.subheadline.weight(.semibold))
                .foregroundColor(.spartanAccent)
                .frame(minHeight: 48)
                .accessibilityHint("Opens the Connections tab")
        }
        .padding(SpartanSpacing.lg)
        .background(
            RoundedRectangle(cornerRadius: SpartanRadius.card)
                .fill(Color.spartanSurface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: SpartanRadius.card)
                .strokeBorder(Color.spartanOutline, lineWidth: 1)
        )
    }
}

private struct LoadingPlan: View {
    var body: some View {
        VStack(alignment: .leading, spacing: SpartanSpacing.md) {
            RoundedRectangle(cornerRadius: 10)
                .fill(Color.spartanSurfaceVariant.opacity(0.5))
                .frame(width: 140, height: 14)
            ForEach(0..<3, id: \.self) { _ in
                RoundedRectangle(cornerRadius: 10)
                    .fill(Color.spartanSurfaceVariant.opacity(0.5))
                    .frame(maxWidth: .infinity)
                    .frame(height: 76)
            }
        }
        .accessibilityLabel("Building today's plan...")
    }
}

private struct EmptyPlan: View {
    var body: some View {
        Text("Nothing scheduled yet. Connect WHOOP or pull the latest data to generate today's plan.")
            .font(.subheadline)
            .foregroundColor(.spartanOnSurface)
            .fixedSize(horizontal: false, vertical: true)
            .padding(SpartanSpacing.xl)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(
                RoundedRectangle(cornerRadius: SpartanRadius.card)
                    .fill(Color.spartanSurface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: SpartanRadius.card)
                    .strokeBorder(Color.spartanOutline, lineWidth: 1)
            )
    }
}

private struct SectionLabel: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.caption.weight(.bold))
            .kerning(1.4)
            .foregroundColor(.spartanOnSurfaceVariant)
            .padding(.top, SpartanSpacing.xs)
    }
}

private struct DetailLabel: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.caption2.weight(.bold))
            .kerning(0.8)
            .foregroundColor(.spartanOnSurfaceVariant)
    }
}

private struct FooterDisclaimer: View {
    var body: some View {
        Text("Spartan offers wellness and fitness guidance, not medical advice. For any health concern, contact a qualified clinician.")
            .font(.caption)
            .foregroundColor(.spartanOnSurfaceVariant)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.vertical, SpartanSpacing.lg)
    }
}

// MARK: - Formatting helpers

/// "Morning", "Midday", ... — same presentation as the Android timeOfDayLabel.
private func timeOfDayLabel(_ time: TimeOfDay) -> String {
    time.rawValue.lowercased().capitalized
}

private let spartanClockFormatter: DateFormatter = {
    let formatter = DateFormatter()
    formatter.dateFormat = "h:mm a"
    return formatter
}()

private func clockTime(fromMillis millis: Int) -> String {
    spartanClockFormatter.string(from: Date(timeIntervalSince1970: TimeInterval(millis) / 1000))
}
