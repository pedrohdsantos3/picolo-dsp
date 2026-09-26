package com.pedro.tone3000m1.data.repository

import com.pedro.tone3000m1.domain.model.LocalNamLibraryItem
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Properties

/** Keeps durable copies of downloaded and user-imported NAM files in app-private storage. */
internal class LocalNamLibraryRepository(filesDirectory: File) {
    private val libraryDirectory = File(filesDirectory, "nam-library").apply { mkdirs() }

    @Synchronized
    fun save(
        source: File,
        modelName: String,
        moduleType: String,
        toneTitle: String = "",
        toneId: String = "",
        imageUrl: String = "",
        modelId: Long = 0L,
    ): LocalNamLibraryItem {
        require(source.isFile && source.length() > 0L) { "NAM file is missing or empty." }
        require(source.extension.equals("nam", ignoreCase = true)) { "Only .nam files are supported." }
        source.inputStream().use { input ->
            return save(input, source.name, modelName, moduleType, toneTitle, toneId, imageUrl, modelId)
        }
    }

    @Synchronized
    fun save(
        input: InputStream,
        originalName: String,
        modelName: String = File(originalName).nameWithoutExtension,
        moduleType: String,
        toneTitle: String = "",
        toneId: String = "",
        imageUrl: String = "",
        modelId: Long = 0L,
    ): LocalNamLibraryItem {
        require(originalName.substringAfterLast('.', "").equals("nam", ignoreCase = true)) {
            "Selecione um arquivo .nam A2."
        }
        val temporary = File.createTempFile("nam-import-", ".tmp", libraryDirectory)
        try {
            input.use { source ->
                temporary.outputStream().use { destination -> source.copyTo(destination) }
            }
            require(temporary.length() > 0L) { "NAM file is empty." }
            val hash = sha256(temporary)
            val destination = File(libraryDirectory, "$hash.nam")
            if (!destination.exists()) check(temporary.renameTo(destination)) { "Could not save NAM file." }
            val normalizedType = moduleType.uppercase().takeIf { it == "PEDAL" } ?: "AMP"
            val metadata = File(libraryDirectory, "$hash.properties")
            val properties = Properties().apply {
                if (metadata.exists()) metadata.inputStream().use(::load)
                setProperty("modelName", modelName.ifBlank { File(originalName).nameWithoutExtension })
                setProperty("modelSize", formatSize(destination.length()))
                setProperty("toneTitle", toneTitle)
                setProperty("toneId", toneId)
                setProperty("imageUrl", imageUrl)
                setProperty("moduleType", normalizedType)
                setProperty("modelId", modelId.toString())
                setProperty("originalName", File(originalName).name)
            }
            metadata.outputStream().use { properties.store(it, "Local NAM library metadata") }
            return item(destination, properties)
        } finally {
            temporary.delete()
        }
    }

    @Synchronized
    fun list(): List<LocalNamLibraryItem> = libraryDirectory.listFiles()
        .orEmpty()
        .filter { it.isFile && it.extension == "nam" && it.length() > 0L }
        .mapNotNull { file ->
            val metadata = File(libraryDirectory, "${file.nameWithoutExtension}.properties")
            if (!metadata.isFile) return@mapNotNull null
            runCatching {
                val properties = Properties().apply { metadata.inputStream().use(::load) }
                item(file, properties)
            }.getOrNull()
        }
        .sortedByDescending { it.file.lastModified() }

    private fun item(file: File, properties: Properties) = LocalNamLibraryItem(
        file = file,
        modelName = properties.getProperty("modelName", file.nameWithoutExtension),
        modelSize = properties.getProperty("modelSize", file.length().toString()),
        toneTitle = properties.getProperty("toneTitle", ""),
        toneId = properties.getProperty("toneId", ""),
        imageUrl = properties.getProperty("imageUrl", ""),
        moduleType = properties.getProperty("moduleType", "AMP"),
        modelId = properties.getProperty("modelId", "0").toLongOrNull() ?: 0L,
    )

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
        bytes >= 1024L -> "%.0f KB".format(bytes / 1024f)
        else -> "$bytes B"
    }
}
