package com.pedro.tone3000m1.data.repository

import android.util.Base64
import com.pedro.tone3000m1.domain.model.ImportedNamFile
import com.pedro.tone3000m1.domain.repository.ToneImportRepository
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.math.roundToInt

/** Owns downloaded and user-imported tone files in the app's private directory. */
internal class ToneImportRepositoryImpl(
    private val filesDirectory: File,
) : ToneImportRepository {
    override fun preparePendingDownload(fileName: String): File {
        require(fileName == File(fileName).name) { "Invalid pending download filename." }
        val destination = File(filesDirectory, fileName)
        if (destination.exists() && !destination.delete()) {
            throw IllegalStateException("Could not clear previous pending model file.")
        }
        return destination
    }

    override fun writeLocalNam(originalName: String, encodedData: String): ImportedNamFile {
        val safeName = originalName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destination = File(filesDirectory, "local-${System.currentTimeMillis()}-$safeName")
        val bytes = Base64.decode(encodedData, Base64.DEFAULT)
        require(bytes.isNotEmpty()) { "Empty local file" }
        destination.writeBytes(bytes)
        return ImportedNamFile(originalName = originalName, file = destination)
    }

    override fun normalizeImpulseResponseWav(source: File): File {
        val bytes = source.readBytes()
        require(bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") { "Invalid WAV header" }
        val view = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var format = 0
        var channels = 0
        var sampleRate = 0
        var bits = 0
        var dataOffset = -1
        var dataSize = 0
        var cursor = 12
        while (cursor + 8 <= bytes.size) {
            val chunk = String(bytes, cursor, 4, Charsets.US_ASCII)
            val size = view.getInt(cursor + 4)
            if (size < 0 || cursor + 8L + size > bytes.size) break
            when (chunk) {
                "fmt " -> if (size >= 16) {
                    format = view.getShort(cursor + 8).toInt() and 0xffff
                    channels = view.getShort(cursor + 10).toInt() and 0xffff
                    sampleRate = view.getInt(cursor + 12)
                    bits = view.getShort(cursor + 22).toInt() and 0xffff
                }
                "data" -> {
                    dataOffset = cursor + 8
                    dataSize = size
                    break
                }
            }
            cursor += 8 + size + (size and 1)
        }
        require(format == 1 || format == 3) { "Unsupported WAV format $format" }
        require(channels > 0 && sampleRate > 0 && dataOffset >= 0) { "Incomplete WAV" }
        require((format == 3 && bits == 32) || (format == 1 && bits in setOf(8, 16, 24, 32))) {
            "Unsupported WAV encoding $format/$bits"
        }
        val bytesPerSample = bits / 8
        val frameBytes = channels * bytesPerSample
        val frames = dataSize / frameBytes
        val output = ByteBuffer.allocate(44 + frames * 2).order(ByteOrder.LITTLE_ENDIAN)
        output.put("RIFF".toByteArray(Charsets.US_ASCII)).putInt(36 + frames * 2)
            .put("WAVEfmt ".toByteArray(Charsets.US_ASCII)).putInt(16)
            .putShort(1).putShort(1).putInt(sampleRate).putInt(sampleRate * 2)
            .putShort(2).putShort(16).put("data".toByteArray(Charsets.US_ASCII)).putInt(frames * 2)

        fun sampleAsFloat(position: Int): Float = when {
            format == 3 -> Float.fromBits(view.getInt(position))
            bits == 8 -> ((bytes[position].toInt() and 0xff) - 128) / 128f
            bits == 16 -> view.getShort(position) / 32768f
            bits == 24 -> {
                val raw = (bytes[position].toInt() and 0xff) or
                    ((bytes[position + 1].toInt() and 0xff) shl 8) or
                    (bytes[position + 2].toInt() shl 16)
                (if (raw and 0x800000 != 0) raw or -0x1000000 else raw) / 8388608f
            }
            else -> view.getInt(position) / 2147483648f
        }
        for (frame in 0 until frames) {
            var mixed = 0f
            val base = dataOffset + frame * frameBytes
            for (channel in 0 until channels) mixed += sampleAsFloat(base + channel * bytesPerSample)
            output.putShort((mixed / channels).coerceIn(-1f, 1f).let { (it * 32767f).roundToInt().toShort() })
        }
        val normalized = File(filesDirectory, "${source.nameWithoutExtension}-pcm16.wav")
        normalized.outputStream().use { it.write(output.array()) }
        return normalized
    }

    override fun commitCurrentModel(pendingFile: File): File {
        val destination = File(filesDirectory, "current-tone3000-model.nam")
        try {
            Files.move(
                pendingFile.toPath(), destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(pendingFile.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        check(destination.exists() && destination.length() > 0L) { "Failed to commit selected model." }
        return destination
    }

    override fun commitExtraModel(pendingFile: File, modelId: Long): File {
        val destination = File(
            filesDirectory,
            "chain-nam-${System.currentTimeMillis()}-$modelId.nam",
        )
        try {
            Files.move(pendingFile.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(pendingFile.toPath(), destination.toPath())
        }
        check(destination.exists() && destination.length() > 0L) { "Failed to commit extra model." }
        return destination
    }
}
