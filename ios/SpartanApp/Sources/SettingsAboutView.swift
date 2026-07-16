// SettingsAboutView.swift — Settings/About: version, disclaimer, privacy note, and
// delete-all-data.
//
// Ports the Android SettingsScreen About card + PrivacyScreen delete flow
// (app/src/main/java/com/spartan/ui/screens/Screens.kt). Copy is verbatim from
// app/src/main/res/values/strings.xml. The delete button erases the local JSON stores
// (PlanStore.eraseAll + SettingsStore.eraseAll via CheckInViewModel.deleteAllData) and
// cancels pending notifications — same local-first, user-owned-data posture as Android.
//
// Honest status: source-complete; awaits an Xcode compile pass (no iOS SDK on the
// authoring machine).

import SwiftUI

struct SettingsAboutView: View {
    @EnvironmentObject private var viewModel: CheckInViewModel
    @State private var confirmDelete = false

    private var versionName: String {
        (Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String) ?? "1.0.0"
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: SpartanSpacing.md) {
                Text("Settings")
                    .font(.largeTitle.weight(.semibold))
                    .foregroundColor(.spartanOnSurface)

                // About card (settings_about / settings_version / settings_disclaimer).
                VStack(alignment: .leading, spacing: 0) {
                    Text("About Spartan")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(.spartanOnSurface)
                    Text("Version \(versionName)")
                        .font(.caption)
                        .foregroundColor(.spartanOnSurfaceVariant)
                        .padding(.top, 4)
                    // iOS has no export flow yet (Android-only for now), so the copy only
                    // claims what this build actually has: on-device storage + deletion.
                    Text("Spartan offers wellness and fitness guidance, not medical advice, and is not a medical device. Your data stays on this device. You can delete all local data below.")
                        .font(.caption)
                        .foregroundColor(.spartanOnSurfaceVariant)
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.top, SpartanSpacing.sm)
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

                // Privacy note (privacy_title / privacy_body).
                VStack(alignment: .leading, spacing: SpartanSpacing.sm) {
                    Text("Privacy")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(.spartanOnSurface)
                    Text("All MVP data is stored on this device. No cloud backend, remote storage, login or account systems, analytics SDKs, telemetry SDKs, advertising SDKs, external health APIs, secrets, API keys, or network calls are included.")
                        .font(.caption)
                        .foregroundColor(.spartanOnSurfaceVariant)
                        .fixedSize(horizontal: false, vertical: true)
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

                // Delete local data (privacy_delete_data + confirmation dialog).
                Button {
                    confirmDelete = true
                } label: {
                    HStack(spacing: SpartanSpacing.sm) {
                        Image(systemName: "trash")
                        Text("Delete local data")
                            .font(.subheadline.weight(.semibold))
                    }
                    .frame(maxWidth: .infinity, minHeight: 48)
                }
                .foregroundColor(.spartanBandRest)
                .overlay(
                    RoundedRectangle(cornerRadius: SpartanRadius.card)
                        .strokeBorder(Color.spartanOutline, lineWidth: 1)
                )

                FooterNote()
            }
            .padding(SpartanSpacing.xl)
        }
        .background(Color.spartanBackground.ignoresSafeArea())
        .alert("Delete local data?", isPresented: $confirmDelete) {
            Button("Delete", role: .destructive) { viewModel.deleteAllData() }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This deletes local health data on this device. This action cannot be undone.")
        }
    }
}

private struct FooterNote: View {
    var body: some View {
        Text("Spartan offers wellness and fitness guidance, not medical advice. For any health concern, contact a qualified clinician.")
            .font(.caption)
            .foregroundColor(.spartanOnSurfaceVariant)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.vertical, SpartanSpacing.lg)
    }
}
