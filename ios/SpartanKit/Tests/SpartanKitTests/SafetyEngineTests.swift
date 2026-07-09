import XCTest
@testable import SpartanKit

/// Broad coverage for the medical-safety guardrail: it must reject over-claiming/unsafe copy (in
/// many surface forms) and accept legitimate wellness language. Port of the blocked/allowed phrase
/// coverage from the Android `SafetyEngineCoverageTest`.
final class SafetyEngineTests: XCTestCase {
    private let safety = SafetyEngine()

    func testRejectsBlockedMedicalClaimsInManyForms() {
        let blocked = [
            "You have diabetes",
            "you have diabetic complications",
            "Your pancreas is overloaded",
            "your pancreas is overloading",
            "You need medication",
            "you need a statin",
            "You need statins now",
            "Take a vitamin D supplement dose",
            "Ignore your doctor",
            "ignore your physician's advice",
            "Just exercise through pain",
        ]
        for copy in blocked {
            XCTAssertFalse(safety.validateCopy(copy), "should block: '\(copy)'")
        }
    }

    func testAcceptsLegitimateWellnessLanguage() {
        let ok = [
            "This value is above the normal clinical range.",
            "This pattern is worth tracking and discussing with a clinician.",
            "One value is not a diagnosis.",
            "Here are safe behavior actions to try this week.",
            "Take a 15-minute easy walk after meals when practical.",
            "If readings stay unusual, consider consulting a qualified clinician.",
            "Prioritize consistent sleep and hydration today.",
        ]
        for copy in ok {
            XCTAssertTrue(safety.validateCopy(copy), "should allow: '\(copy)'")
        }
    }

    func testSanitizeThrowsOnlyForBlockedCopy() {
        // Does not throw for safe copy, and returns it unchanged.
        XCTAssertNoThrow(try safety.sanitize("This is worth discussing with a clinician."))
        XCTAssertEqual(
            try? safety.sanitize("This is worth discussing with a clinician."),
            "This is worth discussing with a clinician."
        )
        // Throws SafetyError.blockedCopy for blocked copy.
        XCTAssertThrowsError(try safety.sanitize("You need medication."), "sanitize must reject blocked copy") { error in
            XCTAssertEqual(error as? SafetyError, SafetyError.blockedCopy)
        }
    }
}
