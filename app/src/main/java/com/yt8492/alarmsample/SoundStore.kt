package com.yt8492.alarmsample

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

data class ImportedSound(val fileName: String, val displayName: String)

class SoundStore(context: Context) {
  private val context = context.applicationContext
  private val directory = File(context.filesDir, "alarm_sounds")

  // 呼び出し側でバックグラウンドスレッドへ切り替える。
  fun import(uri: Uri): ImportedSound {
    check(directory.exists() || directory.mkdirs())
    val destination = File(directory, "${UUID.randomUUID()}.audio")
    try {
      val input = context.contentResolver.openInputStream(uri)
        ?: error("選択した音源を開けませんでした")
      input.use { source ->
        destination.outputStream().use { target ->
          val buffer = ByteArray(8192)
          var total = 0L
          while (true) {
            val size = source.read(buffer)
            if (size < 0) break
            total += size
            check(total <= MAX_BYTES) { "20 MiB以下の音源を選んでください" }
            target.write(buffer, 0, size)
          }
        }
      }
      val metadata = MediaMetadataRetriever()
      try {
        metadata.setDataSource(destination.absolutePath)
        val duration = metadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
          ?.toLongOrNull() ?: 0L
        check(duration > 0L) { "再生可能な音源ではありません" }
      } finally {
        metadata.release()
      }
      return ImportedSound(destination.name, displayName(uri))
    } catch (error: Exception) {
      destination.delete()
      throw error
    }
  }

  fun resolve(fileName: String?): File? {
    if (fileName == null || !fileName.matches(Regex("[a-f0-9-]+\\.audio"))) return null
    return File(directory, fileName).takeIf { it.isFile && it.length() > 0L }
  }

  fun cleanUp(keep: Set<String>) {
    directory.listFiles()?.filter { it.name !in keep }?.forEach { it.delete() }
  }

  private fun displayName(uri: Uri): String {
    return context.contentResolver.query(
      uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
    )?.use { cursor ->
      if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: "選択した音源"
  }

  companion object {
    private const val MAX_BYTES = 20 * 1024 * 1024L
  }
}
