// swift-tools-version: 5.9
// SpartanKit — the shared, platform-neutral core of Spartan for iOS: domain models, the
// rules-based CoachingEngine gated by SafetyEngine, the mock WHOOP source, and scheduling.
// Pure Swift (no UIKit/SwiftUI) so it builds and unit-tests on macOS CI as well as iOS.
import PackageDescription

let package = Package(
    name: "SpartanKit",
    platforms: [.iOS(.v16), .macOS(.v13)],
    products: [
        .library(name: "SpartanKit", targets: ["SpartanKit"]),
    ],
    targets: [
        .target(name: "SpartanKit"),
        // Same suite, two runners: `swift test` needs Xcode's XCTest; SpartanChecks runs the
        // identical assertions as a plain executable on Command Line Tools-only machines/CI.
        .testTarget(name: "SpartanKitTests", dependencies: ["SpartanKit"]),
        .executableTarget(name: "SpartanChecks", dependencies: ["SpartanKit"]),
    ]
)
