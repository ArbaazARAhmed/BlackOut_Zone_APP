package com.blackoutzone.triage

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.system.Os
import android.system.OsConstants
import android.util.Log

/**
 * MediaPipe Gemma needs ~1.3 GB RAM for weights plus cache writes under [Context.getCacheDir].
 * If we call native init when that is not available, the process aborts (SIGABRT) — not catchable in Kotlin.
 */
object OfflineAiCapability {

    private const val TAG = "OfflineAiCapability"
    private const val MIN_AVAILABLE_RAM_BYTES = 1_800_000_000L
    private const val MIN_MEDIAPIPE_CACHE_FREE_BYTES = 700_000_000L
    private const val MIN_RUNTIME_CACHE_FREE_BYTES =
        ModelBundleResolver.EXPECTED_BIN_BYTES + MIN_MEDIAPIPE_CACHE_FREE_BYTES

    data class Assessment(
        val canUseGemma: Boolean,
        val reason: String
    )

    fun assess(context: Context): Assessment {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val availableRam = memoryInfo.availMem
        val totalRam = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            memoryInfo.totalMem
        } else {
            availableRam
        }

        val cacheFree = context.cacheDir.freeSpace
        val filesFree = context.filesDir.freeSpace

        Log.d(
            TAG,
            "Device check: ramAvail=${availableRam / 1_000_000}MB ramTotal=${totalRam / 1_000_000}MB " +
                "cacheFree=${cacheFree / 1_000_000}MB filesFree=${filesFree / 1_000_000}MB"
        )

        if (pageSizeBytes() == 16_384L) {
            return Assessment(
                canUseGemma = false,
                reason = "This device uses 16 KB memory pages; on-device Gemma is disabled for stability."
            )
        }

        if (totalRam < 3_500_000_000L) {
            return Assessment(
                canUseGemma = false,
                reason = "Device RAM is below 4 GB; using local medical protocols only."
            )
        }

        if (availableRam < MIN_AVAILABLE_RAM_BYTES) {
            return Assessment(
                canUseGemma = false,
                reason = "Low free memory (${availableRam / 1_000_000} MB); using local protocols only."
            )
        }

        if (cacheFree < MIN_RUNTIME_CACHE_FREE_BYTES) {
            return Assessment(
                canUseGemma = false,
                reason = "Not enough temporary storage (${cacheFree / 1_000_000} MB free); using local protocols only."
            )
        }

        return Assessment(
            canUseGemma = true,
            reason = "Device meets on-device Gemma requirements."
        )
    }

    fun clearStaleMediapipeCache(context: Context) {
        val cacheDir = context.cacheDir
        cacheDir.listFiles()
            ?.filter { it.name.contains("gemma", ignoreCase = true) && it.name.endsWith(".cache") }
            ?.forEach { file ->
                Log.d(TAG, "Removing stale MediaPipe cache: ${file.name}")
                file.delete()
            }
    }

    fun cacheHasRoomForMediapipe(context: Context): Boolean {
        return context.cacheDir.freeSpace >= MIN_MEDIAPIPE_CACHE_FREE_BYTES
    }

    fun cacheHasRoomForRuntimeModel(context: Context): Boolean {
        return context.cacheDir.freeSpace >= MIN_RUNTIME_CACHE_FREE_BYTES
    }

    private fun pageSizeBytes(): Long {
        return try {
            Os.sysconf(OsConstants._SC_PAGESIZE)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read page size", e)
            4096L
        }
    }
}
