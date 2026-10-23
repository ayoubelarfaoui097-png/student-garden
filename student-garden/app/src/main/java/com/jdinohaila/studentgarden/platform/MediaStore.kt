package com.jdinohaila.studentgarden.platform

import android.content.Context
import android.net.Uri
import com.jdinohaila.studentgarden.domain.newId
import java.io.File
import kotlinx.coroutines.*

class PrivateMedia(private val context: Context) {
    val directory = File(context.filesDir, "media").apply { mkdirs() }

    fun file(id: String): File {
        require(id.matches(Regex("[a-f0-9-]{36}\\.[a-z0-9]{1,5}")))
        return File(directory, id)
    }

    suspend fun saveFrame(bitmap: android.graphics.Bitmap): String = withContext(Dispatchers.IO) {
        val id = "${newId()}.jpg"
        val output = file(id)
        try {
            output.outputStream().use { require(bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it)) }
            require(output.length() in 1..5_000_000)
            id
        } catch (e: Exception) { output.delete(); throw e }
    }

    suspend fun import(uri: Uri, kind: String): String =
        withContext(Dispatchers.IO) {
            val mime = context.contentResolver.getType(uri).orEmpty()
            require(
                if (kind == "image")
                    mime in setOf("image/jpeg", "image/png", "image/webp", "image/gif")
                else
                    mime in
                        setOf(
                            "audio/mpeg",
                            "audio/mp4",
                            "audio/aac",
                            "audio/ogg",
                            "audio/wav",
                            "audio/x-wav",
                            "audio/flac",
                            "audio/webm",
                        )
            ) {
                "Unsupported media file"
            }
            val ext =
                when (mime) {
                    "image/png" -> "png"
                    "image/webp" -> "webp"
                    "image/gif" -> "gif"
                    "image/jpeg" -> "jpg"
                    "audio/ogg" -> "ogg"
                    "audio/wav",
                    "audio/x-wav" -> "wav"
                    "audio/mpeg" -> "mp3"
                    "audio/flac" -> "flac"
                    "audio/webm" -> "webm"
                    else -> "m4a"
                }
            val id = "${newId()}.$ext"
            val dest = file(id)
            try {
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    dest.outputStream().use { output ->
                        val bytes = ByteArray(8192)
                        var size = 0L
                        val limit = if (kind == "image") 5_000_000 else 10_000_000
                        while (true) {
                            val n = input.read(bytes)
                            if (n < 0) break
                            size += n
                            require(size <= limit) { "File is too large" }
                            output.write(bytes, 0, n)
                        }
                        require(size > 0)
                    }
                }
                id
            } catch (e: Exception) {
                dest.delete()
                throw e
            }
        }
}
