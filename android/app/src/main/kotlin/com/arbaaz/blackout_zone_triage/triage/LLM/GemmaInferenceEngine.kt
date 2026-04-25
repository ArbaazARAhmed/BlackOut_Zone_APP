package com.blackoutzone.triage.LLM

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
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
    private var interpreter: Interpreter? = null
    private val inferenceMutex = Mutex()
    private val TAG = "GemmaEngine"

    init {
        interpreter = try {
            val modelFile = File(absoluteModelPath)
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file missing at: $absoluteModelPath")
                null
            } else {
                val readyModel = if (isTarArchive(modelFile)) {
                    extractFirstModelFile(modelFile)
                } else {
                    modelFile
                }

                if (readyModel == null || !readyModel.exists()) {
                    Log.e(TAG, "Failed to resolve LiteRT model from asset.")
                    null
                } else {
                    Interpreter(readyModel, Interpreter.Options().apply { setNumThreads(4) }).also {
                        logModelSchema()
                        Log.d(TAG, "Gemma LiteRT Interpreter initialized: ${readyModel.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Gemma LiteRT model", e)
            null
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
        if (outputFile.exists()) return outputFile

        val success = FileInputStream(tarFile).use { tarStream ->
            val header = ByteArray(512)
            if (tarStream.read(header) != 512) return@use false

            val sizeString = String(header, 124, 12, Charsets.US_ASCII).trim { it <= ' ' }
            val size = sizeString.toLongOrNull(8) ?: return@use false

            FileOutputStream(outputFile).use { out ->
                var remaining = size
                val buffer = ByteArray(8192)
                while (remaining > 0) {
                    val read = tarStream.read(buffer, 0, min(buffer.size.toLong(), remaining).toInt())
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                    remaining -= read
                }
            }

            val padding = ((size + 511) / 512 * 512) - size
            if (padding > 0) {
                tarStream.skip(padding)
            }
            true
        }

        return if (success) outputFile else null
    }

    private fun logModelSchema() {
        interpreter?.let { engine ->
            Log.d(TAG, "TensorFlow Lite model input count=${engine.inputTensorCount}, output count=${engine.outputTensorCount}")
            for (index in 0 until engine.inputTensorCount) {
                val tensor = engine.getInputTensor(index)
                Log.d(TAG, "Input[$index] name=${tensor.name()} shape=${tensor.shape().contentToString()} type=${tensor.dataType()}")
            }
            for (index in 0 until engine.outputTensorCount) {
                val tensor = engine.getOutputTensor(index)
                Log.d(TAG, "Output[$index] name=${tensor.name()} shape=${tensor.shape().contentToString()} type=${tensor.dataType()}")
            }
        }
    }

    suspend fun generateTriageResponse(userPrompt: String): String = withContext(Dispatchers.Default) {
        inferenceMutex.withLock {
            val engine = interpreter ?: return@withLock "AI Engine not initialized."
            return@withLock "LiteRT model loaded. Text generation is not yet implemented for this model type. Check logcat for model input/output details."
        }
    }

    fun getModelSchema(): String {
        return try {
            val engine = interpreter ?: return "Error: Engine not initialized"
            val sb = StringBuilder()
            
            sb.append("=== LiteRT Model Schema ===\n\n")
            sb.append("Input Tensors: ${engine.inputTensorCount}\n")
            for (index in 0 until engine.inputTensorCount) {
                val tensor = engine.getInputTensor(index)
                sb.append("  [$index] ${tensor.name()}\n")
                sb.append("      Shape: ${tensor.shape().contentToString()}\n")
                sb.append("      Type: ${tensor.dataType()}\n")
            }
            
            sb.append("\nOutput Tensors: ${engine.outputTensorCount}\n")
            for (index in 0 until engine.outputTensorCount) {
                val tensor = engine.getOutputTensor(index)
                sb.append("  [$index] ${tensor.name()}\n")
                sb.append("      Shape: ${tensor.shape().contentToString()}\n")
                sb.append("      Type: ${tensor.dataType()}\n")
            }
            
            sb.toString()
        } catch (e: Exception) {
            "Error getting model schema: ${e.message}"
        }
    }
}
