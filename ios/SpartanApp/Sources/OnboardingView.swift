// OnboardingView.swift — first-run onboarding.
//
// Faithful port of the Android OnboardingScreen (app/src/main/java/com/spartan/ui/
// screens/Screens.kt): brand wordmark, tagline, one-line pitch, optional name + height,
// and the wellness-not-medical disclaimer up front. Copy is verbatim from
// app/src/main/res/values/strings.xml.
//
// Honest status: source-complete; awaits an Xcode compile pass (no iOS SDK on the
// authoring machine).

import SwiftUI
import UIKit

struct OnboardingView: View {
    @EnvironmentObject private var viewModel: CheckInViewModel

    @State private var name = ""
    @State private var height = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Spacer().frame(height: 64)
                Text("SPARTAN")
                    .font(.headline.weight(.bold))
                    .kerning(3)
                    .foregroundColor(.spartanAccent)
                Spacer().frame(height: SpartanSpacing.xl)
                Text("Your daily readiness, decided.")
                    .font(.title.weight(.bold))
                    .foregroundColor(.spartanOnSurface)
                    .fixedSize(horizontal: false, vertical: true)
                Text("Connect WHOOP and get one simple, disciplined plan each day to improve recovery, sleep, and fitness — with the reasons behind every action.")
                    .font(.body)
                    .foregroundColor(.spartanOnSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, SpartanSpacing.md)
                Spacer().frame(height: 32)

                // Persistent labels above token-styled fields: a placeholder-as-label vanishes
                // the moment the user types, leaving anonymous boxes (Android keeps its
                // OutlinedTextField floating labels — this is the SwiftUI equivalent).
                SpartanLabeledField(label: "What should we call you?", text: $name)
                Spacer().frame(height: SpartanSpacing.md)
                SpartanLabeledField(label: "Height in cm (optional)", text: $height, keyboard: .decimalPad)
                Spacer().frame(height: SpartanSpacing.lg)

                Button {
                    viewModel.completeOnboarding(name: name, heightCm: Double(height))
                } label: {
                    Text("Begin")
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 52)
                        // Chrome inside the label so SpartanPressStyle scales/dims the whole pill.
                        .foregroundColor(.spartanOnAccent)
                        .background(
                            RoundedRectangle(cornerRadius: SpartanRadius.card)
                                .fill(Color.spartanAccent)
                        )
                }
                .buttonStyle(SpartanPressStyle())

                Text("Wellness and fitness guidance, not medical advice. You control your data and can delete it anytime.")
                    .font(.caption)
                    .foregroundColor(.spartanOnSurfaceVariant)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.top, SpartanSpacing.xl)
            }
            .padding(28)
        }
        .background(Color.spartanBackground.ignoresSafeArea())
    }
}

/// Onboarding text field with a label that persists after input, styled with the Spartan
/// surface/outline/radius tokens instead of the stock system `.roundedBorder`.
private struct SpartanLabeledField: View {
    let label: String
    @Binding var text: String
    var keyboard: UIKeyboardType = .default

    var body: some View {
        VStack(alignment: .leading, spacing: SpartanSpacing.xs) {
            Text(label)
                .font(.caption.weight(.semibold))
                .foregroundColor(.spartanOnSurfaceVariant)
            TextField("", text: $text)
                .textFieldStyle(.plain)
                .keyboardType(keyboard)
                .padding(SpartanSpacing.md)
                .background(
                    RoundedRectangle(cornerRadius: SpartanRadius.chip)
                        .fill(Color.spartanSurface)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: SpartanRadius.chip)
                        .strokeBorder(Color.spartanOutline, lineWidth: 1)
                )
                .accessibilityLabel(label)
        }
    }
}
