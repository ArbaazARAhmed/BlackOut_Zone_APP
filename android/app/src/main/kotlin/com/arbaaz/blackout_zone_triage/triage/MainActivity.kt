package com.blackoutzone.triage

import android.util.Log
import com.blackoutzone.triage.LLM.GemmaInferenceEngine
import com.blackoutzone.triage.LLM.TriageFunctionBridge
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class MainActivity : FlutterActivity() {

    private val channelName = "com.blackoutzone/triage"

    @Volatile
    private var engine: GemmaInferenceEngine? = null

    private val engineInitMutex = Mutex()

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(
            flutterEngine.dartExecutor.binaryMessenger,
            channelName
        ).setMethodCallHandler { call, result ->
            when (call.method) {
                "analyzeSymptoms" -> {
                    val symptoms = call.argument<String>("symptoms") ?: ""
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            val redFlag = withContext(Dispatchers.Default) {
                                RedFlagDetector.evaluate(symptoms)
                            }

                            if (redFlag.triggered) {
                                result.success(
                                    buildString {
                                        append("TRIAGE: ${redFlag.priority}\n")
                                        append("Reason: ${redFlag.reason}\n\n")
                                        append("Immediate Steps:\n")
                                        redFlag.immediateSteps.forEachIndexed { idx, step ->
                                            append("${idx + 1}) $step\n")
                                        }
                                    }
                                )
                                return@launch
                            }

                            val bridge = TriageFunctionBridge(applicationContext)
                            val response = try {
                                val activeEngine = getOrCreateEngine()
                                withContext(Dispatchers.Default) {
                                    activeEngine.generateTriageResponse(symptoms)
                                }
                            } catch (aiError: Exception) {
                                Log.e(TAG, "AI unavailable, using protocol fallback", aiError)
                                ProtocolFallbackTriage.build(
                                    symptoms,
                                    bridge,
                                    aiError.message
                                )
                            }
                            result.success(response)
                        } catch (e: Exception) {
                            Log.e(TAG, "Triage failed", e)
                            result.error(
                                "TRIAGE_ERROR",
                                e.message ?: "Offline triage failed.",
                                null
                            )
                        }
                    }
                }

                "getModelSchema" -> {
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            val activeEngine = getOrCreateEngine()
                            result.success(activeEngine.getModelSchema())
                        } catch (e: Exception) {
                            Log.e(TAG, "Model schema failed", e)
                            result.error(
                                "SCHEMA_ERROR",
                                e.message ?: "Could not load offline AI engine.",
                                null
                            )
                        }
                    }
                }

                else -> result.notImplemented()
            }
        }
    }

    private suspend fun getOrCreateEngine(): GemmaInferenceEngine {
        engine?.let { return it }

        return engineInitMutex.withLock {
            engine?.let { return@withLock it }

            Log.d(TAG, "Preparing model...")
            val modelPath = prepareModel(MODEL_FILE_NAME)

            Log.d(TAG, "Creating engine on background thread...")
            val newEngine = withContext(Dispatchers.IO) {
                GemmaInferenceEngine(
                    applicationContext,
                    modelPath,
                    TriageFunctionBridge(applicationContext)
                )
            }

            if (!newEngine.isReady()) {
                throw IllegalStateException("Gemma readiness failed after initialization.")
            }

            engine = newEngine
            Log.d(TAG, "Gemma ready")
            newEngine
        }
    }

    private suspend fun prepareModel(modelName: String): String = withContext(Dispatchers.IO) {
        val destinationFile = File(applicationContext.filesDir, modelName)
        val tempFile = File(applicationContext.filesDir, "$modelName.tmp")

        if (destinationFile.exists() && isValidModelFile(destinationFile)) {
            Log.d(TAG, "Using cached model bundle at ${destinationFile.absolutePath}")
            return@withContext ModelBundleResolver.resolveInferenceModelPath(
                destinationFile,
                applicationContext.filesDir
            )
        }

        if (destinationFile.exists()) {
            destinationFile.delete()
            Log.d(TAG, "Removed invalid cached model file.")
        }

        if (tempFile.exists()) {
            tempFile.delete()
        }

        val assetBytes = copyModelFromAssets(modelName, tempFile)
        Log.d(TAG, "Copied model size: $assetBytes bytes")

        if (!isValidModelFile(tempFile)) {
            tempFile.delete()
            throw IllegalStateException(
                "Model asset is missing or too small ($assetBytes bytes). " +
                    "Run 'git lfs pull' and rebuild so assets/$modelName is bundled."
            )
        }

        if (!tempFile.renameTo(destinationFile)) {
            tempFile.delete()
            throw IllegalStateException("Could not move model into app storage.")
        }

        Log.d(TAG, "Model bundle cached: ${destinationFile.absolutePath}")
        val inferencePath = ModelBundleResolver.resolveInferenceModelPath(
            destinationFile,
            applicationContext.filesDir
        )
        Log.d(TAG, "Inference model ready: $inferencePath")
        inferencePath
    }

    private fun copyModelFromAssets(modelName: String, tempFile: File): Long {
        val assetManager = applicationContext.assets
        val candidatePaths = listOf(
            "flutter_assets/assets/$modelName",
            "assets/$modelName"
        )

        var lastError: Exception? = null
        for (assetPath in candidatePaths) {
            try {
                assetManager.open(assetPath).use { inputStream ->
                    FileOutputStream(tempFile).use { outputStream ->
                        val buffer = ByteArray(1024 * 1024)
                        var total = 0L
                        while (true) {
                            val read = inputStream.read(buffer)
                            if (read <= 0) break
                            outputStream.write(buffer, 0, read)
                            total += read
                        }
                        outputStream.flush()
                        outputStream.fd.sync()
                        return total
                    }
                }
            } catch (e: Exception) {
                lastError = e
                Log.w(TAG, "Could not open asset at $assetPath", e)
            }
        }

        throw IllegalStateException(
            "Model asset '$modelName' not found in APK. Tried: ${candidatePaths.joinToString()}.",
            lastError
        )
    }

    private fun isValidModelFile(file: File): Boolean {
        return file.exists() && file.length() >= MIN_MODEL_BYTES
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val MODEL_FILE_NAME = "gemma4.task"
        private const val MIN_MODEL_BYTES = 10L * 1024 * 1024
    }
}
