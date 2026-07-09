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
// Honest status: source-complete; awaits an Xcode compile pass (no iOS SDK on the
// authoring machine).

import SwiftUI
import SpartanKit

struct ConnectionsView: View {
    @EnvironmentObject private var viewModel: CheckInViewModel

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
                    onDisconnect: { viewModel.disconnectWhoop() }
                )

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

                Text("Phase note: Spartan currently runs on clearly-labeled sample data. Connecting records your consent locally and, once WHOOP/Google credentials are configured, enables the real OAuth flows. You can disconnect or delete your data anytime.")
                    .font(.caption)
                    .foregroundColor(.spartanOnSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)

                Spacer().frame(height: SpartanSpacing.sm)
            }
            .padding(SpartanSpacing.xl)
        }
        .background(Color.spartanBackground.ignoresSafeArea())
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

    init(
        title: String,
        connected: Bool,
        body: String,
        scopes: [String],
        connectLabel: String,
        onConnect: @escaping () -> Void,
        onDisconnect: @escaping () -> Void
    ) {
        self.title = title
        self.connected = connected
        self.body_ = body
        self.scopes = scopes
        self.connectLabel = connectLabel
        self.onConnect = onConnect
        self.onDisconnect = onDisconnect
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
                Button(action: onDisconnect) {
                    Text("Disconnect")
                        .font(.subheadline.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 48)
                }
                .foregroundColor(.spartanAccent)
                .overlay(
                    RoundedRectangle(cornerRadius: SpartanRadius.card)
                        .strokeBorder(Color.spartanOutline, lineWidth: 1)
                )
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
