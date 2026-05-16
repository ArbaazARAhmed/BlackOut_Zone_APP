package com.blackoutzone.triage

import com.blackoutzone.triage.LLM.TriageFunctionBridge

/**
 * Deterministic offline guidance when on-device Gemma cannot load (low RAM, etc.).
 */
object ProtocolFallbackTriage {

    fun build(symptoms: String, bridge: TriageFunctionBridge, aiError: String?): String {
        val protocols = bridge.lookupRelevantProtocols(symptoms)
        val priority = inferPriority(protocols)

        return buildString {
            appendLine("TRIAGE: $priority")
            appendLine("Reason: Using grounded offline emergency protocols.")
            appendLine()
            appendLine("Recommended actions:")
            if (protocols.contains("No matching protocols") || protocols.contains("No local protocol")) {
                appendLine("1) Keep the person safe and monitor symptoms.")
                appendLine("2) Seek medical help when available.")
                appendLine("3) If symptoms worsen, treat as urgent.")
            } else {
                append(protocols.lines().joinToString("\n") { line ->
                    if (line.startsWith("Steps:")) {
                        line.replace("Steps:", "•")
                    } else {
                        line
                    }
                })
            }
        }.trimEnd()
    }

    private fun inferPriority(protocols: String): String {
        return when {
            protocols.contains("Priority: RED") -> "RED"
            protocols.contains("Priority: YELLOW") -> "YELLOW"
            protocols.contains("Priority: GREEN") -> "GREEN"
            else -> "YELLOW"
        }
    }
}
