package com.blackoutzone.triage

data class RedFlagResult(
    val triggered: Boolean,
    val priority: String,
    val reason: String,
    val immediateSteps: List<String>
)

object RedFlagDetector {
    fun evaluate(symptoms: String): RedFlagResult {
        val s = symptoms.lowercase()

        fun hit(any: List<String>) = any.any { s.contains(it) }

        val unconscious = hit(listOf("unconscious", "not responsive", "no response", "passed out"))
        if (unconscious) {
            return RedFlagResult(
                triggered = true,
                priority = "RED",
                reason = "Unresponsive / altered consciousness",
                immediateSteps = listOf(
                    "Call for urgent medical help immediately.",
                    "Check breathing. If not breathing: start CPR if trained.",
                    "Place in recovery position if breathing and no spine trauma suspected.",
                    "Keep warm; do not give food/drink."
                )
            )
        }

        val severeBleeding = hit(listOf("spurting", "won't stop bleeding", "soaking", "massive bleeding", "hemorrhage"))
        if (severeBleeding) {
            return RedFlagResult(
                triggered = true,
                priority = "RED",
                reason = "Severe bleeding",
                immediateSteps = listOf(
                    "Apply firm direct pressure with clean cloth.",
                    "Add layers if soaked—do not remove the first layer.",
                    "If limb bleed uncontrolled and trained: apply tourniquet above wound.",
                    "Treat for shock: lay flat if safe, keep warm."
                )
            )
        }

        val breathing = hit(listOf("can't breathe", "cannot breathe", "severe shortness of breath", "turning blue", "blue lips", "choking"))
        if (breathing) {
            return RedFlagResult(
                triggered = true,
                priority = "RED",
                reason = "Severe breathing difficulty / choking",
                immediateSteps = listOf(
                    "Sit upright; loosen clothing.",
                    "If choking and cannot speak/cough: perform choking first aid if trained.",
                    "Seek urgent medical help immediately.",
                    "Avoid giving food/drink."
                )
            )
        }

        val chestPain = hit(listOf("crushing chest", "chest pressure", "chest pain")) &&
            hit(listOf("sweating", "shortness of breath", "nausea", "radiating", "left arm", "jaw"))
        if (chestPain) {
            return RedFlagResult(
                triggered = true,
                priority = "RED",
                reason = "Possible cardiac emergency",
                immediateSteps = listOf(
                    "Stop activity; sit/lie down.",
                    "Loosen tight clothing.",
                    "Seek urgent medical help immediately.",
                    "Do not give food/drink."
                )
            )
        }

        val stroke = hit(listOf("face droop", "arm weakness", "slurred speech", "stroke", "can't speak", "one side weak"))
        if (stroke) {
            return RedFlagResult(
                triggered = true,
                priority = "RED",
                reason = "Possible stroke warning signs",
                immediateSteps = listOf(
                    "Treat as emergency; seek urgent medical help immediately.",
                    "Note the time symptoms started.",
                    "Keep person safe and still; do not give food/drink."
                )
            )
        }

        return RedFlagResult(
            triggered = false,
            priority = "UNKNOWN",
            reason = "",
            immediateSteps = emptyList()
        )
    }
}
