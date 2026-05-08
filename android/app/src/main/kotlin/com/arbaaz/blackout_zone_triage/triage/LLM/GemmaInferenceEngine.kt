package com.blackoutzone.triage.LLM

import android.content.Context
import android.util.Log
import com.blackoutzone.triage.LLM.TriageFunctionBridge
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class GemmaInferenceEngine(
    private val context: Context,
    private val absoluteModelPath: String,
    private val bridge: TriageFunctionBridge
) {
    private var llm: LlmInference? = null
    private val inferenceMutex = Mutex()
    private val TAG = "GemmaEngine"

    init {
        try {
            val modelFile = File(absoluteModelPath)
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file missing at: $absoluteModelPath")
            } else {
                llm = createLlmInference(modelFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Gemma LLM", e)
        }
    }

    private fun createLlmInference(modelFile: File): LlmInference? {
        return try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(256)
                .setTopK(40)
                .setTemperature(0.4f)
                .setRandomSeed(42)
                .build()

            LlmInference.createFromOptions(context, options).also {
                Log.d(TAG, "Gemma LLM initialized from ${modelFile.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Gemma LLM", e)
            null
        }
    }

    suspend fun generateTriageResponse(userPrompt: String): String = withContext(Dispatchers.Default) {
        inferenceMutex.withLock {
            val model = llm ?: return@withLock "AI Engine not initialized."
            return@withLock try {
                model.generateResponse(buildPrompt(userPrompt))
            } catch (e: Exception) {
                Log.e(TAG, "Generation failed", e)
                "AI generation failed: ${e.message}"
            }
        }
    }

    fun getModelSchema(): String {
        return llm?.let {
            "Gemma LLM task bundle loaded from $absoluteModelPath. Ready for text generation."
        } ?: "Error: AI Engine not initialized"
    }

    private fun buildPrompt(userPrompt: String): String {
        return """
You are an expert offline medical triage assistant.
Provide a triage level and next-step guidance based on the facts below.
Do not diagnose; be concise and practical.

Symptoms:
$userPrompt

Response format:
TRIAGE: RED/YELLOW/GREEN
Reason: ...
Recommended actions:
1) ...
2) ...
""".trimIndent()
    }
}
