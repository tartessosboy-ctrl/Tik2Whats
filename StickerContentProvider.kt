package com.tik2whats.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

class StickerContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String? {
        val parts = uri.pathSegments
        return when {
            parts.size == 1 && parts[0] == "metadata" -> "vnd.android.cursor.dir/vnd.$AUTHORITY.metadata"
            parts.size == 2 && parts[0] == "stickers" -> "vnd.android.cursor.dir/vnd.$AUTHORITY.stickers"
            parts.size == 3 && parts[0] == "stickers_asset" -> "image/webp"
            else -> null
        }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val context = context ?: throw IllegalStateException("Provider sin contexto")
        val parts = uri.pathSegments
        return if (parts.size == 1 && parts[0] == "metadata") {
            val columns = arrayOf(
                "sticker_pack_identifier", "sticker_pack_name", "sticker_pack_publisher", "sticker_pack_icon",
                "android_play_store_link", "ios_app_store_link", "publisher_email", "publisher_website",
                "privacy_policy_website", "license_agreement_website", "image_data_version", "avoid_cache", "animated_sticker_pack"
            )
            MatrixCursor(columns).apply {
                addRow(arrayOf(MainActivity.PACK_ID, MainActivity.PACK_NAME, "Tik2Whats", "tray.png", "", "", "", "", "", "", "1", 0, 0))
            }
        } else if (parts.size == 2 && parts[0] == "stickers" && parts[1] == MainActivity.PACK_ID) {
            val columns = arrayOf("sticker_file_name", "sticker_emoji", "sticker_accessibility_text", "sticker_is_animated")
            MatrixCursor(columns).apply {
                val dir = File(context.filesDir, "stickers")
                val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".webp", ignoreCase = true) }?.sortedBy { it.name } ?: emptyList()
                files.forEachIndexed { index, file -> addRow(arrayOf(file.name, "🙂", "Tik2Whats sticker ${index + 1}", 0)) }
            }
        } else {
            MatrixCursor(projection ?: emptyArray())
        }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val parts = uri.pathSegments
        if (parts.size != 3 || parts[0] != "stickers_asset" || parts[1] != MainActivity.PACK_ID) {
            throw FileNotFoundException("Ruta de sticker no válida")
        }
        val name = parts[2]
        if (name.contains("..") || name.contains('/')) throw FileNotFoundException("Nombre no válido")
        val file = File(File(context?.filesDir, "stickers"), name)
        if (!file.isFile) throw FileNotFoundException("Sticker no encontrado")
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

    companion object {
        const val AUTHORITY = "com.tik2whats.app.stickercontentprovider"
    }
}
