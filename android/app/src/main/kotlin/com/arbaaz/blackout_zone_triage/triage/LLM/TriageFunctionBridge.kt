package com.blackoutzone.triage.LLM

import android.content.Context
import android.util.Log
import com.blackoutzone.triage.db.BlackoutZoneDatabase
import org.json.JSONObject

class TriageFunctionBridge(private val context: Context) {

    companion object {
        const val SYSTEM_PROMPT_TOOL_BLOCK = """
You are an expert medical triage AI for blackout zone operations. You have access to offline protocols.

Available tools:
- lookup_protocols: Search triage protocols by keywords

Tool format: {"name":"lookup_protocols","arguments":{"keywords":["keyword1","keyword2"]}}

Always use tools when you need specific protocol information. Be concise but thorough.
"""
    }

    private val database = BlackoutZoneDatabase(context)
    private val stopWords = setOf(
        "the", "and", "with", "from", "that", "this", "have", "has", "for", "his", "her",
        "age", "old", "male", "female", "patient", "person", "pain", "very", "after"
    )

    fun dispatch(jsonInput: String): String {
        return try {
            val json = JSONObject(jsonInput.trim())
            val toolName = json.getString("name")

            when (toolName) {
                "lookup_protocols" -> {
                    val args = json.getJSONObject("arguments")
                    val keywordsArray = args.getJSONArray("keywords")
                    val keywords = mutableListOf<String>()
                    for (i in 0 until keywordsArray.length()) {
                        keywords.add(keywordsArray.getString(i))
                    }
                    lookupProtocols(keywords)
                }
                else -> "Unknown tool: $toolName"
            }
        } catch (e: Exception) {
            Log.e("TriageFunctionBridge", "Dispatch error", e)
            "Error processing tool call: ${e.message}"
        }
    }

    fun lookupRelevantProtocols(symptoms: String): String {
        val keywords = symptoms
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 && it !in stopWords }
            .distinct()
            .take(12)

        return lookupProtocols(keywords)
    }

    private fun lookupProtocols(keywords: List<String>): String {
        if (keywords.isEmpty()) {
            return "No local protocol keywords were detected in the symptom text."
        }

        val protocols = database.getProtocolsByKeywords(keywords)
        return if (protocols.isEmpty()) {
            "No matching protocols found for keywords: ${keywords.joinToString(", ")}"
        } else {
            protocols.joinToString("\n\n") { protocol ->
                """
Protocol: ${protocol.injuryCategory}
Priority: ${protocol.triagePriority}
Symptoms: ${protocol.symptomsKeywords}
Steps: ${protocol.treatmentSteps}
""".trimIndent()
            }
        }
    }
}
