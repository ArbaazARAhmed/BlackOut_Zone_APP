package com.blackoutzone.triage

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * MediaPipe LlmInference expects a LiteRT .bin or a zip-based .task bundle.
 * Our shipped gemma4.task is a POSIX tar containing a single .bin file.
 */
object ModelBundleResolver {

    private const val TAG = "ModelBundleResolver"
    private const val MIN_MODEL_BYTES = 10L * 1024 * 1024
    // Size of gemma-1.1-2b-it-cpu-int4.bin inside the shipped gemma4.task tar bundle.
    const val EXPECTED_BIN_BYTES = 1_346_427_328L

    fun resolveInferenceModelPath(bundleFile: File, outputDir: File): String {
        if (!bundleFile.exists()) {
            throw IllegalStateException("Model bundle missing: ${bundleFile.absolutePath}")
        }

        if (bundleFile.name.endsWith(".bin", ignoreCase = true)) {
            return bundleFile.absolutePath
        }

        if (isTarArchive(bundleFile)) {
            return extractTarBundle(bundleFile, outputDir)
        }

        // Zip-based MediaPipe .task bundles can be passed through directly.
        return bundleFile.absolutePath
    }

    private fun extractTarBundle(tarFile: File, outputDir: File): String {
        outputDir.mkdirs()
        val existingBin = outputDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".bin", ignoreCase = true) }
            ?.firstOrNull { isValidInferenceModel(it) }

        if (existingBin != null) {
            Log.d(TAG, "Using cached extracted model: ${existingBin.absolutePath}")
            return existingBin.absolutePath
        }

        outputDir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".bin", ignoreCase = true) }
            ?.forEach { stale ->
                Log.w(TAG, "Removing stale extracted model: ${stale.name} (${stale.length()} bytes)")
                stale.delete()
            }

        Log.d(TAG, "Extracting tar model bundle: ${tarFile.absolutePath}")
        val extracted = extractFirstRegularFile(tarFile, outputDir)
        if (!isValidInferenceModel(extracted)) {
            extracted.delete()
            throw IllegalStateException(
                "Extracted model is invalid at ${extracted.absolutePath} (${extracted.length()} bytes)"
            )
        }
        Log.d(TAG, "Extracted inference model: ${extracted.absolutePath} (${extracted.length()} bytes)")
        return extracted.absolutePath
    }

    fun extractTarAsset(input: InputStream, assetName: String, outputDir: File): String {
        outputDir.mkdirs()
        clearRuntimeDirectory(outputDir)

        Log.d(TAG, "Extracting tar model asset: $assetName")
        val extracted = extractFirstRegularFile(input, assetName, outputDir)
        if (!isValidInferenceModel(extracted)) {
            extracted.delete()
            throw IllegalStateException(
                "Extracted model is invalid at ${extracted.absolutePath} (${extracted.length()} bytes)"
            )
        }
        Log.d(TAG, "Extracted inference model: ${extracted.absolutePath} (${extracted.length()} bytes)")
        return extracted.absolutePath
    }

    fun clearRuntimeDirectory(outputDir: File) {
        outputDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                clearRuntimeDirectory(file)
            }
            if (!file.delete()) {
                Log.w(TAG, "Could not delete runtime model file: ${file.absolutePath}")
            }
        }
    }

    fun isValidInferenceModel(file: File): Boolean {
        if (!file.exists() || file.length() < MIN_MODEL_BYTES) {
            return false
        }
        if (file.length() != EXPECTED_BIN_BYTES) {
            return false
        }
        return hasTfliteMagic(file)
    }

    private fun hasTfliteMagic(file: File): Boolean {
        return try {
            FileInputStream(file).use { input ->
                val header = ByteArray(8)
                if (input.read(header) != 8) {
                    return false
                }
                String(header, 4, 4, StandardCharsets.US_ASCII) == "TFL3"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Could not read model header for ${file.name}", e)
            false
        }
    }

    private fun isTarArchive(file: File): Boolean {
        FileInputStream(file).use { input ->
            val header = ByteArray(512)
            if (input.read(header) != 512) {
                return false
            }
            if (header.all { it == 0.toByte() }) {
                return false
            }
            val magic = String(header, 257, 5, StandardCharsets.US_ASCII)
            if (magic == "ustar") {
                return true
            }
            val name = readTarString(header, 0, 100)
            return name.endsWith(".bin", ignoreCase = true)
        }
    }

    private fun extractFirstRegularFile(tarFile: File, outputDir: File): File {
        FileInputStream(tarFile).use { input ->
            return extractFirstRegularFile(input, tarFile.name, outputDir)
        }
    }

    private fun extractFirstRegularFile(input: InputStream, sourceName: String, outputDir: File): File {
        while (true) {
            val header = ByteArray(512)
            val headerRead = input.read(header)
            if (headerRead <= 0) {
                break
            }
            if (headerRead < 512) {
                throw IllegalStateException("Unexpected end of tar header in $sourceName")
            }
            if (header.all { it == 0.toByte() }) {
                break
            }

            val nameField = readTarString(header, 0, 100)
            val prefix = readTarString(header, 345, 155)
            val entryName = if (prefix.isNotEmpty()) {
                "$prefix/$nameField"
            } else {
                nameField
            }
            val entryType = header[156].toInt().toChar()
            val size = readTarOctal(header, 124, 12)

            if (entryType == '0' || entryType == '\u0000') {
                if (size <= 0L) {
                    skipTarPadding(input, 0L)
                    continue
                }
                val safeName = File(entryName).name
                if (safeName.isEmpty()) {
                    skipTarContent(input, size)
                    continue
                }
                val outFile = File(outputDir, safeName)
                val tempFile = File(outputDir, "$safeName.tmp")
                if (tempFile.exists()) {
                    tempFile.delete()
                }

                FileOutputStream(tempFile).use { output ->
                    var remaining = size
                    val buffer = ByteArray(1024 * 1024)
                    while (remaining > 0L) {
                        val toRead = minOf(remaining, buffer.size.toLong()).toInt()
                        val read = input.read(buffer, 0, toRead)
                        if (read <= 0) {
                            throw IllegalStateException("Unexpected end of tar entry: $entryName")
                        }
                        output.write(buffer, 0, read)
                        remaining -= read
                    }
                    output.flush()
                    output.fd.sync()
                }

                if (outFile.exists()) {
                    outFile.delete()
                }
                if (!tempFile.renameTo(outFile)) {
                    tempFile.delete()
                    throw IllegalStateException("Could not finalize extracted model: $safeName")
                }
                skipTarPadding(input, size)
                return outFile
            }

            skipTarContent(input, size)
        }

        throw IllegalStateException("No model file found inside tar bundle: $sourceName")
    }

    private fun skipTarContent(input: InputStream, size: Long) {
        var remaining = size
        val skipBuffer = ByteArray(1024 * 1024)
        while (remaining > 0L) {
            val toSkip = minOf(remaining, skipBuffer.size.toLong()).toInt()
            val skipped = input.read(skipBuffer, 0, toSkip)
            if (skipped <= 0) {
                break
            }
            remaining -= skipped
        }
        skipTarPadding(input, size)
    }

    private fun skipTarPadding(input: InputStream, size: Long) {
        val remainder = (512L - (size % 512L)) % 512L
        if (remainder > 0L) {
            input.skip(remainder)
        }
    }

    private fun readTarString(header: ByteArray, offset: Int, length: Int): String {
        val end = offset + length
        var actualEnd = offset
        while (actualEnd < end && header[actualEnd] != 0.toByte()) {
            actualEnd++
        }
        return String(header, offset, actualEnd - offset, StandardCharsets.UTF_8).trim()
    }

    private fun readTarOctal(header: ByteArray, offset: Int, length: Int): Long {
        val raw = readTarString(header, offset, length)
        if (raw.isEmpty()) {
            return 0L
        }
        return raw.trim { it <= ' ' }.toLong(8)
    }
}
