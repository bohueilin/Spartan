package com.vitalcompass.domain.engine

class SafetyEngine {
    private val blockedPhrases = listOf(
        "you have " + "diabetes",
        "your pancreas is " + "overloaded",
        "you need " + "medication",
        "take x " + "supplement dose",
        "you need " + "statins",
        "ignore your " + "doctor",
        "exercise through " + "pain",
    )

    fun validateCopy(copy: String): Boolean {
        val normalized = copy.lowercase()
        return blockedPhrases.none { normalized.contains(it) }
    }

    fun sanitize(copy: String): String {
        require(validateCopy(copy)) {
            "Health copy contains blocked medical overclaiming or unsafe advice."
        }
        return copy
    }
}
