import Foundation

/// Thrown by `SafetyEngine.sanitize(_:)` when copy contains blocked medical
/// overclaiming or unsafe advice.
public enum SafetyError: Error, Equatable {
    case blockedCopy
}

/// Guardrail for every user-facing coaching string. Faithful port of
/// `com.spartan.domain.engine.SafetyEngine` (Android): the same six blocked patterns over the
/// same normalization (lowercased, non-alphanumeric runs collapsed to a single space, trimmed).
///
/// Spartan gives wellness / fitness / recovery guidance — never diagnoses, never prescribes.
public final class SafetyEngine {

    private let blockedPatterns: [NSRegularExpression]
    private let nonAlphanumericRuns: NSRegularExpression

    public init() {
        let patterns = [
            #"\byou\s+have\s+diabet(es|ic)\b"#,
            #"\byour\s+pancreas\s+is\s+overload(ed|ing)\b"#,
            #"\byou\s+need\s+(a\s+)?(medication|medicine|statin|statins)\b"#,
            #"\btake\s+.+\s+supplement\s+dose\b"#,
            #"\bignore\s+your\s+(doctor|clinician|physician)\b"#,
            #"\bexercise\s+through\s+pain\b"#,
        ]
        // Constant, hand-verified patterns — a failure here is a programmer error.
        self.blockedPatterns = patterns.map { pattern in
            try! NSRegularExpression(pattern: pattern, options: [])
        }
        self.nonAlphanumericRuns = try! NSRegularExpression(pattern: #"[^a-z0-9]+"#, options: [])
    }

    /// True when `copy` contains none of the blocked medical-overclaiming / unsafe-advice forms.
    public func validateCopy(_ copy: String) -> Bool {
        let normalized = normalize(copy)
        let range = NSRange(normalized.startIndex..<normalized.endIndex, in: normalized)
        return blockedPatterns.allSatisfy { pattern in
            pattern.firstMatch(in: normalized, options: [], range: range) == nil
        }
    }

    /// Returns `copy` unchanged, or throws `SafetyError.blockedCopy` if it fails validation.
    @discardableResult
    public func sanitize(_ copy: String) throws -> String {
        guard validateCopy(copy) else { throw SafetyError.blockedCopy }
        return copy
    }

    /// Returns `copy` unchanged, halting on violation. Mirrors Kotlin's `require`: blocked copy
    /// coming out of the rules engine is a generation-time invariant failure, never expected at
    /// runtime, so it is treated as a programmer error rather than a recoverable condition.
    @discardableResult
    public func sanitizeOrFatal(_ copy: String) -> String {
        guard validateCopy(copy) else {
            preconditionFailure("Health copy contains blocked medical overclaiming or unsafe advice.")
        }
        return copy
    }

    /// Lowercase, collapse every non-alphanumeric run to a single space, and trim.
    private func normalize(_ copy: String) -> String {
        let lowered = copy.lowercased()
        let range = NSRange(lowered.startIndex..<lowered.endIndex, in: lowered)
        return nonAlphanumericRuns
            .stringByReplacingMatches(in: lowered, options: [], range: range, withTemplate: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
