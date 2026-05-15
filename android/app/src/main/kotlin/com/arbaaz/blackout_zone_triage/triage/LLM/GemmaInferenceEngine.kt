package com.blackoutzone.triage.LLM

import android.content.Context
import android.util.Log
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
        initializeEngine()
    }

    private fun initializeEngine() {

        try {

            val modelFile = File(absoluteModelPath)

            if (!modelFile.exists()) {
                throw Exception(
                    "Model file missing at: $absoluteModelPath"
                )
            }

            Log.d(
                TAG,
                "Model found: ${modelFile.absolutePath}"
            )

            Log.d(
                TAG,
                "Model size: ${modelFile.length()}"
            )

            val options =
                LlmInference.LlmInferenceOptions
                    .builder()
                    .setModelPath(modelFile.absolutePath)
                    .setMaxTokens(256)
                    .setTopK(40)
                    .setTemperature(0.4f)
                    .setRandomSeed(42)
                    .build()

            llm =
                LlmInference.createFromOptions(
                    context,
                    options
                )

            Log.d(
                TAG,
                "Gemma initialized successfully"
            )

        } catch (e: Exception) {

            llm = null

            Log.e(
                TAG,
                "Gemma init failed",
                e
            )

            throw RuntimeException(
                "Gemma initialization failed",
                e
            )
        }
    }

    fun isReady(): Boolean {
        return llm != null
    }

    suspend fun generateTriageResponse(
        userPrompt: String
    ): String = withContext(Dispatchers.Default) {

        inferenceMutex.withLock {

            val model = llm

            if (model == null) {
                return@withLock "Offline AI engine unavailable."
            }

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

    private fun buildPrompt(
        userPrompt: String
    ): String {

        val protocols =
            bridge.lookupRelevantProtocols(
                userPrompt
            )

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