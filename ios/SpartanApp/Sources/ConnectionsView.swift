// ConnectionsView.swift — consent + integration management.
//
// Faithful port of the Android ConnectionsScreen (app/src/main/java/com/spartan/ui/
// screens/ConnectionsScreen.kt): explains exactly what Spartan reads and why, in plain
// language, before any connection. Least privilege: WHOOP read-only health scopes;
// Google Calendar free/busy only (never event contents) unless the user opts into event
// creation. All copy is verbatim from app/src/main/res/values/strings.xml. Connection
// state is stored in the local JSON SettingsStore (no UserDefaults), recording consent
// on-device only.
//
// WHOOP CSV import (the no-credentials path to real data): the WHOOP card carries an
// "Import WHOOP export (.csv)" action that opens the system file picker. Reading the
// security-scoped URLs and parsing happens here in the UI layer; the merged
// WhoopImportData is handed to the view model, which persists it and rebuilds today's
// plan (mirrors WhoopCsvImporter.import + MainViewModel.importWhoopCsv on Android).
//
// Honest status: source-complete; awaits an Xcode compile pass (no iOS SDK on the
// authoring machine).

import SwiftUI
import UniformTypeIdentifiers
import SpartanKit

struct ConnectionsView: View {
    @EnvironmentObject private var viewModel: CheckInViewModel
    @State private var showWhoopImporter = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpartanSpacing.lg) {
                Text("Connections")
                    .font(.largeTitle.weight(.semibold))
                    .foregroundColor(.spartanOnSurface)
                Text("Spartan treats your health, activity, and calendar data as sensitive. Data stays on your device, is used only to build your plan, and is never sold or used for ads.")
                    .font(.subheadline)
                    .foregroundColor(.spartanOnSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)

                IntegrationCard(
                    title: "WHOOP",
                    connected: viewModel.whoopConnected,
                    body: "Reads your recovery, sleep, strain, HRV, resting heart rate, and respiratory rate to personalize your daily plan. Read-only. No data is written back to WHOOP.",
                    scopes: [
                        "read:recovery, read:sleep — recovery and sleep",
                        "read:cycles, read:workout — strain and workouts",
                        "read:profile — basic profile",
                    ],
                    connectLabel: viewModel.whoopIsMock ? "Use sample data" : "Connect WHOOP",
                    onConnect: { viewModel.connectWhoop() },
                    onDisconnect: { viewModel.disconnectWhoop() },
                    secondaryActionLabel: "Import WHOOP export (.csv)",
                    onSecondaryAction: { showWhoopImporter = true },
                    secondaryActionHint: "No developer credentials needed: in the WHOOP app go to App Settings → Data Export, then pick the exported CSV files here. Your data stays on this device."
                )

                if let importState = viewModel.whoopImport {
                    WhoopImportResultCard(state: importState) {
                        viewModel.dismissWhoopImportResult()
                    }
                }

                IntegrationCard(
                    title: "Google Calendar",
                    connected: viewModel.calendarConnected,
                    body: "Reads your free/busy times only — never event titles or details — to schedule activities into open windows. Creating calendar events is a separate, optional step you confirm each time.",
                    scopes: [
                        "calendar.freebusy — busy/free times only",
                        "calendar.events — only if you opt in to adding events",
                    ],
                    connectLabel: "Connect Calendar",
                    onConnect: { viewModel.connectCalendar() },
                    onDisconnect: { viewModel.disconnectCalendar() }
                )

                Text("Phase note: without a connection Spartan runs on clearly-labeled sample data. Import your WHOOP CSV export for real data with no credentials, or connect the OAuth flows once WHOOP/Google credentials are configured. You can disconnect or delete your data anytime.")
                    .font(.caption)
                    .foregroundColor(.spartanOnSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)

                Spacer().frame(height: SpartanSpacing.sm)
            }
            .padding(SpartanSpacing.xl)
        }
        .background(Color.spartanBackground.ignoresSafeArea())
        .fileImporter(
            isPresented: $showWhoopImporter,
            allowedContentTypes: [.commaSeparatedText, .plainText, .data],
            allowsMultipleSelection: true
        ) { result in
            if case let .success(urls) = result {
                importWhoopExport(urls)
            }
        }
    }

    /// Reads the picked export files (each wrapped in security-scoped access), parses them
    /// with WhoopCsvParser, merges per day with WhoopCsvMerger, and hands the result to the
    /// view model — the iOS analogue of WhoopCsvImporter.import's read loop. Unreadable or
    /// unrecognized files land in `skipped` and are reported, never crashed on.
    private func importWhoopExport(_ urls: [URL]) {
        guard !urls.isEmpty else { return }
        var parsed: [ParsedWhoopFile] = []
        var recognized: [String] = []
        var skipped: [String] = []
        for url in urls {
            let name = url.lastPathComponent
            let accessing = url.startAccessingSecurityScopedResource()
            defer { if accessing { url.stopAccessingSecurityScopedResource() } }
            guard let text = try? String(contentsOf: url, encoding: .utf8),
                  let file = WhoopCsvParser.parse(text) else {
                skipped.append(name)
                continue
            }
            parsed.append(file)
            recognized.append(name)
        }
        let data = WhoopCsvMerger.merge(
            files: parsed,
            importedAtMillis: Int(Date().timeIntervalSince1970 * 1000)
        )
        viewModel.applyWhoopImport(data, recognizedFiles: recognized, skippedFiles: skipped)
    }
}

private struct IntegrationCard: View {
    let title: String
    let connected: Bool
    let body_: String
    let scopes: [String]
    let connectLabel: String
    let onConnect: () -> Void
    let onDisconnect: () -> Void
    let secondaryActionLabel: String?
    let onSecondaryAction: (() -> Void)?
    let secondaryActionHint: String?

    init(
        title: String,
        connected: Bool,
        body: String,
        scopes: [String],
        connectLabel: String,
        onConnect: @escaping () -> Void,
        onDisconnect: @escaping () -> Void,
        secondaryActionLabel: String? = nil,
        onSecondaryAction: (() -> Void)? = nil,
        secondaryActionHint: String? = nil
    ) {
        self.title = title
        self.connected = connected
        self.body_ = body
        self.scopes = scopes
        self.connectLabel = connectLabel
        self.onConnect = onConnect
        self.onDisconnect = onDisconnect
        self.secondaryActionLabel = secondaryActionLabel
        self.onSecondaryAction = onSecondaryAction
        self.secondaryActionHint = secondaryActionHint
    }

    var body: some View {
        VStack(alignment: .leading, spacing: SpartanSpacing.sm) {
            HStack {
                Text(title)
                    .font(.headline.weight(.semibold))
                    .foregroundColor(.spartanOnSurface)
                Spacer()
                if connected { ConnectedChip() }
            }
            Text(body_)
                .font(.subheadline)
                .foregroundColor(.spartanOnSurfaceVariant)
                .fixedSize(horizontal: false, vertical: true)
            Text("What Spartan requests:")
                .font(.footnote.weight(.semibold))
                .foregroundColor(.spartanOnSurface)
            ForEach(scopes, id: \.self) { scope in
                Text("• \(scope)")
                    .font(.caption)
                    .foregroundColor(.spartanOnSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)
            }
            Spacer().frame(height: SpartanSpacing.xs)
            if connected {
                OutlinedCardButton(label: "Disconnect", action: onDisconnect)
            } else {
                Button(action: onConnect) {
                    Text(connectLabel)
                        .font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 48)
                }
                .foregroundColor(.spartanOnAccent)
                .background(
                    RoundedRectangle(cornerRadius: SpartanRadius.card)
                        .fill(Color.spartanAccent)
                )
            }
            if let secondaryActionLabel, let onSecondaryAction {
                OutlinedCardButton(label: secondaryActionLabel, action: onSecondaryAction)
                if let secondaryActionHint {
                    Text(secondaryActionHint)
                        .font(.caption)
                        .foregroundColor(.spartanOnSurfaceVariant)
                        .fixedSize(horizontal: false, vertical: true)
                }
            }
        }
        .padding(SpartanSpacing.lg)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.spartanSurface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(Color.spartanOutline, lineWidth: 1)
        )
    }
}

/// Outcome of a WHOOP CSV import: progress, a summary of what landed, or a gentle failure.
/// Copy mirrors Android's connections_import_* strings verbatim.
private struct WhoopImportResultCard: View {
    let state: WhoopImportUiState
    let onDismiss: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: SpartanSpacing.sm) {
            if state.inProgress {
                Text("Importing your WHOOP data…")
                    .font(.subheadline)
                    .foregroundColor(.spartanOnSurface)
            } else if state.failed {
                Text("Import didn't work")
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(.spartanOnSurface)
                Text(failureBody)
                    .font(.caption)
                    .foregroundColor(.spartanOnSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)
                OutlinedCardButton(label: "Dismiss", action: onDismiss)
            } else if let summary = state.summary {
                Text("Your WHOOP data is in")
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(.spartanOnSurface)
                Text(successBody(summary))
                    .font(.caption)
                    .foregroundColor(.spartanOnSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)
                if !summary.skippedFiles.isEmpty {
                    Text("Skipped (not recognized): \(summary.skippedFiles.joined(separator: ", "))")
                        .font(.caption)
                        .foregroundColor(.spartanOnSurfaceVariant)
                        .fixedSize(horizontal: false, vertical: true)
                }
                OutlinedCardButton(label: "Dismiss", action: onDismiss)
            }
        }
        .padding(SpartanSpacing.lg)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.spartanSurface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(Color.spartanOutline, lineWidth: 1)
        )
    }

    private var failureBody: String {
        if state.failedButRecognized.isEmpty {
            return "None of the selected files looked like a WHOOP export. Pick the CSV files from your export (physiological_cycles, sleeps, workouts, journal_entries) and try again."
        }
        return "Recognized \(state.failedButRecognized.joined(separator: ", ")), but Spartan needs physiological_cycles, sleeps, or workouts to build a plan — journal entries alone aren't enough. Add those files and import again."
    }

    private func successBody(_ summary: WhoopImportSummary) -> String {
        let range = "\(Self.dayFormatter.string(from: date(fromEpochDay: summary.firstDayEpoch))) – \(Self.dayFormatter.string(from: date(fromEpochDay: summary.lastDayEpoch)))"
        return "Imported \(summary.days) days of your real WHOOP data (\(range)) with \(summary.workouts) workouts and \(summary.journalDays) journal days. Today's plan and your WHOOP metrics now run on your data instead of the sample series."
    }

    /// Epoch days count LOCAL wall-clock dates (see WhoopCsvParser), so they are rendered
    /// back through a fixed UTC calendar — the same date math as Android's
    /// LocalDate.ofEpochDay + "MMM d".
    private static let dayFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.dateFormat = "MMM d"
        return formatter
    }()

    private func date(fromEpochDay day: Int) -> Date {
        Date(timeIntervalSince1970: TimeInterval(day) * 86_400)
    }
}

/// The outlined secondary button used across the Connections cards (Android OutlinedButton).
private struct OutlinedCardButton: View {
    let label: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(label)
                .font(.subheadline.weight(.semibold))
                .frame(maxWidth: .infinity, minHeight: 48)
        }
        .foregroundColor(.spartanAccent)
        .overlay(
            RoundedRectangle(cornerRadius: SpartanRadius.card)
                .strokeBorder(Color.spartanOutline, lineWidth: 1)
        )
    }
}

private struct ConnectedChip: View {
    var body: some View {
        Text("CONNECTED")
            .font(.caption2.weight(.bold))
            .foregroundColor(.spartanAccent)
            .padding(.horizontal, SpartanSpacing.sm)
            .padding(.vertical, 3)
            .background(
                RoundedRectangle(cornerRadius: 6)
                    .fill(Color.spartanAccent.opacity(0.16))
            )
    }
}
