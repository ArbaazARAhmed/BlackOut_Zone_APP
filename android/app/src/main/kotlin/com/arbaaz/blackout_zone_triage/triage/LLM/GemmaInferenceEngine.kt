package com.blackoutzone.triage.LLM

import android.content.Context
import android.util.Log
import com.blackoutzone.triage.ModelBundleResolver
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class GemmaInferenceEngine(
    private val context: Context,
    private val absoluteModelPath: String,
    private val bridge: TriageFunctionBridge
) {

    private var llm: LlmInference? = null
    private val inferenceMutex = Mutex()
    private val TAG = "GemmaEngine"

    init {
        initializeEngine()
    }

    private fun initializeEngine() {
        val modelFile = File(absoluteModelPath)
        try {
            if (!modelFile.exists()) {
                throw IllegalStateException("Model file missing at: $absoluteModelPath")
            }

            Log.d(TAG, "Loading model: ${modelFile.absolutePath} (${modelFile.length()} bytes)")

            if (!ModelBundleResolver.isValidInferenceModel(modelFile)) {
                throw IllegalStateException(
                    "Invalid LiteRT model at ${modelFile.name} (${modelFile.length()} bytes). " +
                        "Clear app storage and retry."
                )
            }

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelFile.absolutePath)
                .setMaxTokens(256)
                .setTopK(40)
                .setTemperature(0.4f)
                .setRandomSeed(42)
                .build()

            llm = LlmInference.createFromOptions(context, options)
            Log.d(TAG, "Gemma initialized successfully")
        } catch (e: Exception) {
            llm = null
            Log.e(TAG, "Gemma init failed for ${modelFile.absolutePath}", e)
            val detail = e.message ?: e.javaClass.simpleName
            val cause = e.cause?.message
            val message = if (cause != null && cause != detail) {
                "Gemma initialization failed: $detail ($cause)"
            } else {
                "Gemma initialization failed: $detail"
            }
            throw IllegalStateException(message, e)
        }
    }

    fun isReady(): Boolean = llm != null

    suspend fun generateTriageResponse(userPrompt: String): String = withContext(Dispatchers.Default) {
        inferenceMutex.withLock {
            val model = llm ?: return@withLock "Offline AI engine unavailable."
            try {
                model.generateResponse(buildPrompt(userPrompt))
            } catch (e: Exception) {
                Log.e(TAG, "Generation failed", e)
                "AI generation failed: ${e.message}"
            }
        }
    }

    fun getModelSchema(): String {
        val modelFile = File(absoluteModelPath)
        return if (llm != null) {
            buildString {
                appendLine("Status: Gemma offline engine ready")
                appendLine("Model: ${modelFile.name}")
                appendLine("Path: ${modelFile.absolutePath}")
                appendLine("Size: ${modelFile.length()} bytes")
                appendLine("Max tokens: 256")
            }.trimEnd()
        } else {
            "Status: Gemma engine unavailable (not initialized)."
        }
    }

    private fun buildPrompt(userPrompt: String): String {
        val protocols = bridge.lookupRelevantProtocols(userPrompt)
        return """
You are an offline emergency medical triage assistant.

Provide concise first-aid guidance.

Symptoms:
$userPrompt

Local protocols:
$protocols

Response format:

TRIAGE: RED/YELLOW/GREEN

Reason: ...

Recommended actions:
1) ...
2) ...
""".trimIndent()
    }
}
