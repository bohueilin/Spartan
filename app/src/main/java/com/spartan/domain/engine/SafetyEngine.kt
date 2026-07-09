package com.spartan.domain.engine

class SafetyEngine {
    private val blockedPatterns = listOf(
        Regex("""\byou\s+have\s+diabet(es|ic)\b"""),
        Regex("""\byour\s+pancreas\s+is\s+overload(ed|ing)\b"""),
        Regex("""\byou\s+need\s+(a\s+)?(medication|medicine|statin|statins)\b"""),
        Regex("""\btake\s+.+\s+supplement\s+dose\b"""),
        Regex("""\bignore\s+your\s+(doctor|clinician|physician)\b"""),
        Regex("""\bexercise\s+through\s+pain\b"""),
    )

    fun validateCopy(copy: String): Boolean {
        val normalized = copy
            .lowercase()
            .replace(Regex("""[^\p{Alnum}]+"""), " ")
            .trim()
        return blockedPatterns.none { it.containsMatchIn(normalized) }
    }

    fun sanitize(copy: String): String {
        require(validateCopy(copy)) {
            "Health copy contains blocked medical overclaiming or unsafe advice."
        }
        return copy
    }
}
