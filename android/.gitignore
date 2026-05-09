package com.blackoutzone.triage.LLM

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.min

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
                val readyModel = if (isTarArchive(modelFile)) {
                    extractFirstModelFile(modelFile)
                } else {
                    modelFile
                }

                if (readyModel == null || !readyModel.exists()) {
                    Log.e(TAG, "Failed to resolve Gemma model from bundle.")
                } else {
                    llm = createLlmInference(readyModel)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Gemma LLM", e)
        }
    }

    private fun isTarArchive(file: File): Boolean {
        return try {
            RandomAccessFile(file, "r").use {
                it.seek(257)
                val signature = ByteArray(5)
                it.readFully(signature)
                String(signature, Charsets.US_ASCII) == "ustar"
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun extractFirstModelFile(tarFile: File): File? {
        val outputFile = File(context.filesDir, tarFile.nameWithoutExtension + ".bin")
        val tempFile = File(context.filesDir, outputFile.name + ".tmp")

        return try {
            FileInputStream(tarFile).use { tarStream ->
                val header = ByteArray(512)
                if (tarStream.read(header) != 512) return null

                val entryName = String(header, 0, 100, Charsets.US_ASCII).trim('\u0000', ' ')
                val sizeString = String(header, 124, 12, Charsets.US_ASCII).trim('\u0000', ' ')
                val expectedBytes = sizeString.toLongOrNull(8) ?: return null

                if (outputFile.exists() && outputFile.length() == expectedBytes) {
                    Log.d(TAG, "Verified extracted model: ${outputFile.name}")
                    return outputFile
                }

                if (outputFile.exists()) outputFile.delete()
                if (tempFile.exists()) tempFile.delete()

                FileOutputStream(tempFile).use { out ->
                    var remaining = expectedBytes
                    val buffer = ByteArray(1024 * 1024)
                    while (remaining > 0) {
                        val read = tarStream.read(buffer, 0, min(buffer.size.toLong(), remaining).toInt())
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        remaining -= read
                    }
                    out.flush()
                    out.fd.sync()
                }

                if (tempFile.length() != expectedBytes) {
                    tempFile.delete()
                    Log.e(TAG, "Incomplete model extraction: ${tempFile.length()} of $expectedBytes bytes")
                    return null
                }

                if (!tempFile.renameTo(outputFile)) {
                    tempFile.delete()
                    Log.e(TAG, "Failed to atomically install extracted model")
                    return null
                }

                Log.d(TAG, "Extracted $entryName to ${outputFile.name}")
                outputFile
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract Gemma model bundle", e)
            null
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
        val localProtocols = bridge.lookupRelevantProtocols(userPrompt)

        return """
You are an expert offline medical triage assistant.
Provide a triage level and next-step guidance based on the facts below.
Do not diagnose; be concise and practical.
Use the local medical protocols as grounding. If they conflict with general knowledge, follow the local protocols.

Symptoms:
$userPrompt

Local protocols:
$localProtocols

Response format:
TRIAGE: RED/YELLOW/GREEN
Reason: ...
Recommended actions:
1) ...
2) ...
""".trimIndent()
    }
}