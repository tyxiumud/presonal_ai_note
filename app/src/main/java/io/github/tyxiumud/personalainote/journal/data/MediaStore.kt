package io.github.tyxiumud.personalainote.journal.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import io.github.tyxiumud.personalainote.journal.model.CaptureType
import java.io.File
import java.io.FileInputStream
import java.util.UUID

/** 把系统返回的 Uri 复制进应用私有目录，避免依赖外部文件的长期读权限。 */
interface MediaStore {
    fun import(uri: Uri, type: CaptureType): String?

    fun delete(absolutePath: String)

    fun file(absolutePath: String): File

    /** 导入备份时把媒体字节写进私有目录，返回新的绝对路径。 */
    fun writeMedia(fileName: String, bytes: ByteArray): String?
}

class LocalMediaStore(context: Context) : MediaStore {
    private val applicationContext = context.applicationContext
    private val root by lazy {
        File(applicationContext.filesDir, "media").apply { mkdirs() }
    }

    override fun import(uri: Uri, type: CaptureType): String? {
        val extension = when (type) {
            CaptureType.IMAGE -> "img"
            CaptureType.AUDIO -> "audio"
            CaptureType.TEXT -> return null
        }
        val target = File(root, "${extension}_${System.currentTimeMillis()}_${UUID.randomUUID()}")
        return try {
            val input = if (ContentResolver.SCHEME_FILE == uri.scheme && uri.path != null) {
                FileInputStream(File(uri.path!!))
            } else {
                applicationContext.contentResolver.openInputStream(uri)
            }
            input?.use { source ->
                target.outputStream().use { output -> source.copyTo(output) }
            } ?: return null
            target.absolutePath
        } catch (_: Exception) {
            target.delete()
            null
        }
    }

    override fun delete(absolutePath: String) {
        runCatching { File(absolutePath).delete() }
    }

    override fun file(absolutePath: String): File = File(absolutePath)

    override fun writeMedia(fileName: String, bytes: ByteArray): String? = try {
        val target = File(root, "imported_${System.currentTimeMillis()}_$fileName")
        target.writeBytes(bytes)
        target.absolutePath
    } catch (_: Exception) {
        null
    }
}
