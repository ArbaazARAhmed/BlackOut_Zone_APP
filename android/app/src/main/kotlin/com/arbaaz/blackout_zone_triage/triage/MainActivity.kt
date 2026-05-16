package com.blackoutzone.triage

import android.util.Log
import com.blackoutzone.triage.LLM.GemmaInferenceEngine
import com.blackoutzone.triage.LLM.TriageFunctionBridge
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.File
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

    @Volatile
    private var deviceAssessment: OfflineAiCapability.Assessment? = null

    private val engineInitMutex = Mutex()

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
    super.configureFlutterEngine(flutterEngine)

    clearOldCache()
    clearLegacyPersistentModelCopies()

        deviceAssessment = OfflineAiCapability.assess(applicationContext)
        Log.i(TAG, "Offline AI assessment: ${deviceAssessment?.reason}")
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
                            val assessment = deviceAssessment
                                ?: OfflineAiCapability.assess(applicationContext).also {
                                    deviceAssessment = it
                                }

                            val response = if (!assessment.canUseGemma) {
                                Log.i(TAG, "Protocol-only triage: ${assessment.reason}")
                                ProtocolFallbackTriage.build(
                                    symptoms,
                                    bridge,
                                    assessment.reason
                                )
                            } else {
                                try {
                                    OfflineAiCapability.clearStaleMediapipeCache(applicationContext)
                                    if (!OfflineAiCapability.cacheHasRoomForMediapipe(applicationContext)) {
                                        ProtocolFallbackTriage.build(
                                            symptoms,
                                            bridge,
                                            null
                                        )
                                    } else {
                                        val activeEngine = getOrCreateEngine()
                                            withContext(Dispatchers.Default) {
                                            activeEngine.generateTriageResponse(symptoms)
                                        }
                                    }
                                } catch (aiError: Exception) {
                                    Log.e(TAG, "AI unavailable, using protocol fallback", aiError)
                                    ProtocolFallbackTriage.build(
                                        symptoms,
                                        bridge,
                                        null
                                    )
                                }
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
                    val assessment = deviceAssessment
                        ?: OfflineAiCapability.assess(applicationContext).also {
                            deviceAssessment = it
                        }
                    if (!assessment.canUseGemma) {
                        result.success(
                            buildString {
                                appendLine("Status: Protocol-only mode (Gemma disabled)")
                                appendLine("Reason: ${assessment.reason}")
                                appendLine("Red-flag rules: enabled")
                                appendLine("SQLite protocols: enabled")
                            }.trimEnd()
                        )
                        return@setMethodCallHandler
                    }

                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            val schema = withContext(Dispatchers.IO) {
                                buildModelSchema(assessment)
                            }
                            result.success(schema)
                        } catch (e: Exception) {
                            Log.e(TAG, "Model schema failed", e)
                            result.success(
                                "Status: Gemma unavailable\nDetail: ${e.message}\nFallback: SQLite protocols"
                            )
                        }
                    }
                }

                "getOfflineMode" -> {
                    val assessment = deviceAssessment
                        ?: OfflineAiCapability.assess(applicationContext).also {
                            deviceAssessment = it
                        }
                    result.success(
                        mapOf(
                            "gemmaEnabled" to assessment.canUseGemma,
                            "message" to assessment.reason
                        )
                    )
                }

                else -> result.notImplemented()
            }
        }
    }

    override fun onDestroy() {

    try {

        engine?.close()
        engine = null

        OfflineAiCapability
            .clearStaleMediapipeCache(applicationContext)

        Log.d(
            TAG,
            "Gemma resources cleaned"
        )

    } catch (e: Exception) {

        Log.e(
            TAG,
            "Cleanup failed",
            e
        )
    }

    super.onDestroy()
}

    private suspend fun getOrCreateEngine(): GemmaInferenceEngine {
        val assessment = deviceAssessment
            ?: OfflineAiCapability.assess(applicationContext).also { deviceAssessment = it }
        if (!assessment.canUseGemma) {
            throw IllegalStateException(assessment.reason)
        }

        engine?.let { return it }

        return engineInitMutex.withLock {
            engine?.let { return@withLock it }

            if (!OfflineAiCapability.cacheHasRoomForMediapipe(applicationContext)) {
                throw IllegalStateException(
                    "Need at least 700 MB free app cache before loading Gemma."
                )
            }
            if (!OfflineAiCapability.cacheHasRoomForRuntimeModel(applicationContext)) {
                throw IllegalStateException(
                    "Need temporary storage for Gemma runtime model before loading."
                )
            }

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
        val runtimeDir = runtimeModelDir()
        val assetPath = findModelAssetPath(modelName)
        val inferencePath = applicationContext.assets.open(assetPath).use { input ->
            ModelBundleResolver.extractTarAsset(input, assetPath, runtimeDir)
        }
        Log.d(TAG, "Inference model ready: $inferencePath")
        inferencePath
    }

    private fun buildModelSchema(assessment: OfflineAiCapability.Assessment): String {
        val assetPath = findModelAssetPath(MODEL_FILE_NAME)
        val assetBytes = applicationContext.assets.openFd(assetPath).use { descriptor ->
            descriptor.length
        }
        return buildString {
            appendLine("Status: Gemma on-device engine available")
            appendLine("Mode: phone-only offline inference")
            appendLine("Model asset: $assetPath")
            appendLine("Asset size: $assetBytes bytes")
            appendLine("Runtime file: app cache, reused while installed")
            appendLine("Persistent model copy: disabled")
            appendLine("Cleanup: stale persistent copies removed on startup")
            appendLine("Device check: ${assessment.reason}")
            appendLine("Max tokens: 96")
        }.trimEnd()
    }

    private fun findModelAssetPath(modelName: String): String {
        val assetManager = applicationContext.assets
        val candidatePaths = listOf(
            "flutter_assets/assets/$modelName",
            "assets/$modelName"
        )

        var lastError: Exception? = null
        for (assetPath in candidatePaths) {
            try {
                assetManager.openFd(assetPath).close()
                return assetPath
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

    private fun runtimeModelDir(): File {
        return File(applicationContext.cacheDir, RUNTIME_MODEL_DIR)
    }
    private fun clearOldCache() {

    try {

        cacheDir.listFiles()?.forEach { file ->

            if (
                file.name.contains("mediapipe", true) ||
                file.name.contains("gemma", true) ||
                file.name.contains("llm", true)
            ) {
                file.deleteRecursively()
            }
        }

        Log.d(
            "MainActivity",
            "AI cache cleared"
        )

    } catch (e: Exception) {

        Log.e(
            "MainActivity",
            "Cache clear failed",
            e
        )
    }
}
    private fun clearLegacyPersistentModelCopies() {
        applicationContext.filesDir.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.name.startsWith("gemma", ignoreCase = true) &&
                    (
                        file.name.endsWith(".task", ignoreCase = true) ||
                            file.name.endsWith(".task.tmp", ignoreCase = true) ||
                            file.name.endsWith(".bin", ignoreCase = true) ||
                            file.name.endsWith(".bin.tmp", ignoreCase = true)
                    )
            }
            ?.forEach { file ->
                if (file.delete()) {
                    Log.d(TAG, "Deleted legacy persistent model copy: ${file.name}")
                } else {
                    Log.w(TAG, "Could not delete legacy persistent model copy: ${file.absolutePath}")
                }
            }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val MODEL_FILE_NAME = "gemma4.task"
        private const val RUNTIME_MODEL_DIR = "gemma_runtime"
    }
}
