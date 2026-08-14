// SpartanApp.swift — app entry point + Spartan design tokens for iOS.
//
// Faithful port of the Android theme (app/src/main/java/com/spartan/ui/theme/Theme.kt and
// Tokens.kt): OLED-dark-forward with a single athletic teal accent, and the WCAG-audited
// light-mode band variants (the bright band colors fail 3:1 on light surfaces, so light
// mode uses darkened equivalents, all >= 4.5:1 on white — see docs/ACCESSIBILITY.md in the
// Android tree).
//
// Honest status: this SwiftUI layer is source-complete but awaits an Xcode compile pass
// (xcodegen generate + xcodebuild) on a machine with the iOS SDK.

import SwiftUI
import UIKit
import SpartanKit

@main
struct SpartanIOSApp: App {
    @StateObject private var viewModel = CheckInViewModel()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(viewModel)
                .tint(.spartanAccent)
        }
    }
}

/// Onboarding-gated root: onboarding first, then the tabbed app (Today / Connections / Settings).
/// The selection binding lets the check-in's connect prompt route to the Connections tab —
/// the screen whose labels are honest about sample data vs. CSV import — mirroring Android's
/// onManageConnections navigation (CheckInScreen.kt ConnectPrompt).
struct RootView: View {
    @EnvironmentObject private var viewModel: CheckInViewModel
    @State private var selectedTab: SpartanTab = .today

    enum SpartanTab: Hashable {
        case today, connections, settings
    }

    var body: some View {
        if viewModel.onboardingComplete {
            TabView(selection: $selectedTab) {
                CheckInView(onManageConnections: { selectedTab = .connections })
                    .tabItem { Label("Today", systemImage: "heart") }
                    .tag(SpartanTab.today)
                ConnectionsView()
                    .tabItem { Label("Connections", systemImage: "link") }
                    .tag(SpartanTab.connections)
                SettingsAboutView()
                    .tabItem { Label("Settings", systemImage: "gearshape") }
                    .tag(SpartanTab.settings)
            }
        } else {
            OnboardingView()
        }
    }
}

// MARK: - Spartan theme tokens

extension Color {
    /// Theme-aware color: resolves per the current light/dark trait, like Compose's
    /// isSystemInDarkTheme() switch in Theme.kt.
    static func spartanDynamic(light: UInt32, dark: UInt32) -> Color {
        Color(uiColor: UIColor { traits in
            traits.userInterfaceStyle == .dark ? UIColor(spartanRGB: dark) : UIColor(spartanRGB: light)
        })
    }

    // Core scheme (Theme.kt LightColors / DarkColors).
    static let spartanBackground = spartanDynamic(light: 0xF6F8F8, dark: 0x0A0F0E)
    static let spartanSurface = spartanDynamic(light: 0xFFFFFF, dark: 0x121817)
    static let spartanSurfaceVariant = spartanDynamic(light: 0xE6ECEB, dark: 0x1E2A26)
    // Light accent/tertiary darkened one step (0x0E7C6E→0x0B685C, 0x9A6A1B→0x7E5613) so 11pt-bold
    // chip text passes 4.5:1 on its own 12–16% tinted containers, not just on plain surfaces —
    // mirrors the same fix in the Android Theme.kt.
    static let spartanAccent = spartanDynamic(light: 0x0B685C, dark: 0x3FE0C8)
    static let spartanOnAccent = spartanDynamic(light: 0xFFFFFF, dark: 0x04211D)
    static let spartanSecondary = spartanDynamic(light: 0x2C3A44, dark: 0xB7C4C2)
    static let spartanTertiary = spartanDynamic(light: 0x7E5613, dark: 0xE7B25A)
    static let spartanOnSurface = spartanDynamic(light: 0x11201E, dark: 0xEAF1EF)
    static let spartanOnSurfaceVariant = spartanDynamic(light: 0x4A5654, dark: 0x9DB0AB)
    static let spartanOutline = spartanDynamic(light: 0x9BADAA, dark: 0x3D4F48)

    // Readiness band colors (Tokens.kt SpartanBands), incl. WCAG light-mode variants
    // (easy/rest darkened so band text passes 4.5:1 on 18%-alpha tinted chips too).
    static let spartanBandPrimed = spartanDynamic(light: 0x0E7B43, dark: 0x38D07E)
    static let spartanBandEasy = spartanDynamic(light: 0x7C570E, dark: 0xE7B25A)
    static let spartanBandRest = spartanDynamic(light: 0xA0381D, dark: 0xE67A5A)
}

private extension UIColor {
    convenience init(spartanRGB rgb: UInt32) {
        self.init(
            red: CGFloat((rgb >> 16) & 0xFF) / 255.0,
            green: CGFloat((rgb >> 8) & 0xFF) / 255.0,
            blue: CGFloat(rgb & 0xFF) / 255.0,
            alpha: 1.0
        )
    }
}

/// The single place readiness band -> color is defined (Tokens.kt bandColor).
/// BALANCED uses the accent; nil (no data) uses the muted variant.
func spartanBandColor(_ band: ReadinessBand?) -> Color {
    switch band {
    case .primed: return .spartanBandPrimed
    case .balanced: return .spartanAccent
    case .easy: return .spartanBandEasy
    case .rest: return .spartanBandRest
    case nil: return .spartanOnSurfaceVariant
    }
}

/// Same user-facing band labels as Android (Tokens.kt bandLabel).
func spartanBandLabel(_ band: ReadinessBand) -> String {
    switch band {
    case .primed: return "Primed"
    case .balanced: return "Balanced"
    case .easy: return "Take it easy"
    case .rest: return "Recovery day"
    }
}

/// Spacing scale (Tokens.kt Spacing): 4/8/12/16/20/24.
enum SpartanSpacing {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 20
    static let xxl: CGFloat = 24
}

/// Radii (Tokens.kt Radius).
enum SpartanRadius {
    static let chip: CGFloat = 8
    static let card: CGFloat = 18
}

/// Shared press feedback (Android's Material ripple equivalent): 0.96 scale + slight dim while
/// pressed, opacity-only under reduced motion, and dimmed when disabled — the explicit brand
/// colors on these buttons opt out of the system's automatic disabled tint.
struct SpartanPressStyle: ButtonStyle {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.isEnabled) private var isEnabled
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(reduceMotion ? 1 : (configuration.isPressed ? 0.96 : 1))
            .opacity(isEnabled ? (configuration.isPressed ? 0.85 : 1) : 0.5)
            .animation(.easeOut(duration: 0.14), value: configuration.isPressed)
    }
}
