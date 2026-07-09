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
                    .kerning(4)
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

                TextField("What should we call you?", text: $name)
                    .textFieldStyle(.roundedBorder)
                Spacer().frame(height: SpartanSpacing.md)
                TextField("Height in cm (optional)", text: $height)
                    .textFieldStyle(.roundedBorder)
                    .keyboardType(.decimalPad)
                Spacer().frame(height: SpartanSpacing.lg)

                Button {
                    viewModel.completeOnboarding(name: name, heightCm: Double(height))
                } label: {
                    Text("Begin")
                        .font(.body.weight(.semibold))
                        .frame(maxWidth: .infinity, minHeight: 52)
                }
                .foregroundColor(.spartanOnAccent)
                .background(
                    RoundedRectangle(cornerRadius: SpartanRadius.card)
                        .fill(Color.spartanAccent)
                )

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
