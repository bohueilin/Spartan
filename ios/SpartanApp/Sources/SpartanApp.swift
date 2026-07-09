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
struct RootView: View {
    @EnvironmentObject private var viewModel: CheckInViewModel

    var body: some View {
        if viewModel.onboardingComplete {
            TabView {
                CheckInView()
                    .tabItem { Label("Today", systemImage: "heart") }
                ConnectionsView()
                    .tabItem { Label("Connections", systemImage: "link") }
                SettingsAboutView()
                    .tabItem { Label("Settings", systemImage: "gearshape") }
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
    static let spartanAccent = spartanDynamic(light: 0x0E7C6E, dark: 0x3FE0C8)
    static let spartanOnAccent = spartanDynamic(light: 0xFFFFFF, dark: 0x04211D)
    static let spartanSecondary = spartanDynamic(light: 0x2C3A44, dark: 0xB7C4C2)
    static let spartanTertiary = spartanDynamic(light: 0x9A6A1B, dark: 0xE7B25A)
    static let spartanOnSurface = spartanDynamic(light: 0x11201E, dark: 0xEAF1EF)
    static let spartanOnSurfaceVariant = spartanDynamic(light: 0x4A5654, dark: 0x9DB0AB)
    static let spartanOutline = spartanDynamic(light: 0xC3CFCD, dark: 0x293630)

    // Readiness band colors (Tokens.kt SpartanBands), incl. WCAG light-mode variants.
    static let spartanBandPrimed = spartanDynamic(light: 0x0E7B43, dark: 0x38D07E)
    static let spartanBandEasy = spartanDynamic(light: 0x8F6410, dark: 0xE7B25A)
    static let spartanBandRest = spartanDynamic(light: 0xB23E20, dark: 0xE67A5A)
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
