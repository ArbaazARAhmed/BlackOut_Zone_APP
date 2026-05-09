package com.blackoutzone.triage

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import com.blackoutzone.triage.LLM.GemmaInferenceEngine
import com.blackoutzone.triage.LLM.TriageFunctionBridge
import com.blackoutzone.triage.RedFlagDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import android.util.Log

class MainActivity : FlutterActivity() {
    private val CHANNEL = "com.blackoutzone/triage"

    // @Volatile ensures the engine instance is visible across threads immediately
    @Volatile private var engine: GemmaInferenceEngine? = null

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "analyzeSymptoms" -> {
                        val symptoms = call.argument<String>("symptoms") ?: ""

                        // We use Main scope for UI interaction, but offload heavy tasks
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                // 1. FAST PATH: Check Hard-coded Red Flags (Medical Safety)
                                val redFlag = RedFlagDetector.evaluate(symptoms)
                                if (redFlag.triggered) {
                                    val fastResponse = buildString {
                                        append("TRIAGE: ${redFlag.priority}\n")
                                        append("Reason: ${redFlag.reason}\n\n")
                                        append("Immediate Life-Saving Steps:\n")
                                        redFlag.immediateSteps.forEachIndexed { idx: Int, step: String ->
                                        append("${idx + 1}) $step\n")
                                        }
                                        append("\nNote: AI analysis bypassed for critical emergency.")
                                    }
                                    result.success(fastResponse)
                                    return@launch // Stop here for emergencies
                                }

                                // 2. AI PATH: Prepare/Retrieve the Engine
                                val appContext = applicationContext
                                val activeEngine = engine ?: withContext(Dispatchers.IO) {
                                    val modelName = "gemma4.task"
                                    val modelPath = prepareModel(modelName)
                                    val bridge = TriageFunctionBridge(appContext)
                                    
                                    Log.d("MainActivity", "Initializing new Gemma Engine session...")
                                    val newEngine = GemmaInferenceEngine(appContext, modelPath, bridge)
                                    
                                    // Save for next call
                                    engine = newEngine
                                    newEngine 
                                }

                                // 3. GENERATE: Get nuanced AI triage
                                val response = activeEngine.generateTriageResponse(symptoms)
                                result.success(response)

                            } catch (e: Exception) {
                                Log.e("MainActivity", "Triage Process Failed", e)
                                result.error("TRIAGE_ERROR", "Engine failed: ${e.message}", null)
                            }
                        }
                    }
                    "getModelSchema" -> {
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                val appContext = applicationContext
                                val activeEngine = engine ?: withContext(Dispatchers.IO) {
                                    val modelName = "gemma4.task"
                                    val modelPath = prepareModel(modelName)
                                    val bridge = TriageFunctionBridge(appContext)
                                    
                                    Log.d("MainActivity", "Initializing new Gemma Engine session...")
                                    val newEngine = GemmaInferenceEngine(appContext, modelPath, bridge)
                                    
                                    // Save for next call
                                    engine = newEngine
                                    newEngine 
                                }

                                val schema = activeEngine.getModelSchema()
                                result.success(schema)
                            } catch (e: Exception) {
                                Log.e("MainActivity", "Failed to get model schema", e)
                                result.error("SCHEMA_ERROR", "Failed: ${e.message}", null)
                            }
                        }
                    }
                    else -> result.notImplemented()
                }
            }
    }

    /**
     * Ensures the 1.3GB model is extracted safely to internal storage.
     * Includes length checks and physical disk sync to prevent corruption.
     */
    private suspend fun prepareModel(modelName: String): String = withContext(Dispatchers.IO) {
        val destinationFile = File(context.filesDir, modelName)
        val tempFile = File(context.filesDir, "$modelName.tmp")
        val assetPath = "flutter_assets/assets/$modelName"
        val assetManager = context.assets
        val expectedBytes = assetManager.openFd(assetPath).use { it.length }
        
        if (destinationFile.exists()) {
            if (destinationFile.length() == expectedBytes) {
                Log.d("MainActivity", "Verified existing model: ${destinationFile.length()} bytes")
                return@withContext destinationFile.absolutePath
            } else {
                Log.w("MainActivity", "Model size mismatch (${destinationFile.length()} != $expectedBytes). Re-extracting...")
                destinationFile.delete()
            }
        }

        try {
            if (tempFile.exists()) tempFile.delete()

            assetManager.open(assetPath).use { inputStream ->
                FileOutputStream(tempFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                    
                    // ATOMIC SYNC: Force the OS to finish writing before we return
                    outputStream.flush()
                    outputStream.getFD().sync() 
                }
            }

            if (tempFile.length() != expectedBytes) {
                tempFile.delete()
                throw Exception("Asset copy incomplete: ${tempFile.length()} of $expectedBytes bytes")
            }

            if (!tempFile.renameTo(destinationFile)) {
                tempFile.delete()
                throw Exception("Atomic model install failed")
            }

            Log.d("MainActivity", "Model extraction complete. Sync successful.")
        } catch (e: Exception) {
            Log.e("MainActivity", "Fatal extraction error", e)
            throw Exception("Asset extraction failed: ${e.message}")
        }

        return@withContext destinationFile.absolutePath
    }
}
