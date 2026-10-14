package com.tik2whats.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

class MainActivity : ComponentActivity() {
    private lateinit var status: TextView
    private lateinit var count: TextView
    private lateinit var preview: ImageView
    private lateinit var stickerList: LinearLayout
    private var currentUri: Uri? = null

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) importImage(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ensureStickerDir()
        buildUi()
        handleIntent(intent)
        refreshStickerList()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun buildUi() {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 28)
            setBackgroundColor(0xFFF4F7FB.toInt())
        }
        outer.addView(TextView(this).apply {
            text = "Tik2Whats"
            textSize = 30f
            gravity = Gravity.CENTER
            setTextColor(0xFF172033.toInt())
        }, match(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        outer.addView(TextView(this).apply {
            text = "Crea un paquete de stickers estáticos para WhatsApp"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(0xFF4B5563.toInt())
            setPadding(0, 8, 0, 18)
        }, match(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        outer.addView(Button(this).apply {
            text = "Elegir imagen o GIF"
            setOnClickListener { pickImage.launch("image/*") }
        }, match(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        preview = ImageView(this).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFFE4E9F0.toInt())
            contentDescription = "Vista previa del sticker seleccionado"
        }
        outer.addView(preview, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 210).apply { topMargin = 14 })

        outer.addView(Button(this).apply {
            text = "Añadir imagen al paquete"
            setOnClickListener {
                val uri = currentUri
                if (uri == null) {
                    toast("Primero elige una imagen.")
                } else {
                    saveSticker(uri)
                }
            }
        }, match(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 10 })

        status = TextView(this).apply {
            text = "Elige una imagen para empezar."
            textSize = 14f
            setTextColor(0xFF374151.toInt())
            setPadding(0, 12, 0, 4)
        }
        outer.addView(status, match(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        count = TextView(this).apply {
            textSize = 16f
            setTextColor(0xFF172033.toInt())
            setPadding(0, 12, 0, 8)
        }
        outer.addView(count, match(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        stickerList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        outer.addView(stickerList, match(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        outer.addView(Button(this).apply {
            text = "Añadir paquete a WhatsApp"
            setOnClickListener { addPackToWhatsApp() }
        }, match(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = 16 })

        outer.addView(TextView(this).apply {
            text = "Versión inicial: procesa imágenes estáticas (también puede usar el primer fotograma de algunos GIF). Los vídeos y los stickers animados todavía no están implementados. Para instalar el paquete hacen falta entre 3 y 30 stickers y WhatsApp instalado."
            textSize = 12f
            setTextColor(0xFF6B7280.toInt())
            setPadding(0, 14, 0, 0)
        }, match(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        setContentView(ScrollView(this).apply { addView(outer) })
    }

    private fun match(width: Int, height: Int) = LinearLayout.LayoutParams(width, height)

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val uri = when (intent.action) {
            Intent.ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            }
            Intent.ACTION_VIEW -> intent.data
            else -> null
        }
        if (uri != null) {
            currentUri = uri
            showPreview(uri)
        }
    }

    private fun importImage(uri: Uri) {
        currentUri = uri
        showPreview(uri)
        status.text = "Imagen seleccionada. Pulsa «Añadir imagen al paquete»."
    }

    private fun showPreview(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { stream ->
                preview.setImageBitmap(BitmapFactory.decodeStream(stream))
            }
        } catch (_: Exception) {
            preview.setImageDrawable(null)
            status.text = "No he podido abrir esta imagen. Prueba con otro archivo."
        }
    }

    private fun ensureStickerDir(): File = File(filesDir, "stickers").apply { mkdirs() }

    private fun stickerFiles(): List<File> = ensureStickerDir().listFiles { file ->
        file.isFile && file.name.endsWith(".webp", ignoreCase = true)
    }?.sortedBy { it.name } ?: emptyList()

    private fun saveSticker(uri: Uri) {
        if (stickerFiles().size >= 30) {
            toast("El paquete ya tiene 30 stickers, el máximo permitido.")
            return
        }
        try {
            val bitmap = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
            if (bitmap == null) {
                status.text = "No se pudo decodificar el archivo. Prueba con una imagen PNG o JPEG."
                return
            }
            val square = toStickerCanvas(bitmap)
            bitmap.recycle()
            val dir = ensureStickerDir()
            val index = (stickerFiles().size + 1).coerceAtMost(30)
            val target = File(dir, "sticker_${index.toString().padStart(2, '0')}.webp")
            var quality = 90
            var ok = false
            while (quality >= 15) {
                FileOutputStream(target).use { out ->
                    val format = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
                    square.compress(format, quality, out)
                }
                if (target.length() <= 100 * 1024) { ok = true; break }
                quality -= 10
            }
            square.recycle()
            if (!ok) {
                target.delete()
                status.text = "La imagen ocupa demasiado incluso al comprimirla. Prueba otra imagen más sencilla."
                return
            }
            updateTrayIcon(target)
            status.text = "Sticker añadido (${target.length() / 1024} KB)."
            currentUri = null
            preview.setImageDrawable(null)
            refreshStickerList()
        } catch (e: Exception) {
            status.text = "No se pudo guardar el sticker: ${e.localizedMessage ?: "error desconocido"}"
        }
    }

    private fun toStickerCanvas(source: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(android.graphics.Color.TRANSPARENT)
        val scale = min(480f / source.width, 480f / source.height)
        val w = source.width * scale
        val h = source.height * scale
        val left = (512f - w) / 2f
        val top = (512f - h) / 2f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, null, RectF(left, top, left + w, top + h), paint)
        return result
    }

    private fun updateTrayIcon(sticker: File) {
        try {
            val bitmap = BitmapFactory.decodeFile(sticker.absolutePath) ?: return
            val icon = Bitmap.createScaledBitmap(bitmap, 96, 96, true)
            FileOutputStream(File(ensureStickerDir(), "tray.png")).use { icon.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            icon.recycle()
        } catch (_: Exception) { }
    }

    private fun refreshStickerList() {
        val files = stickerFiles()
        count.text = "Stickers en el paquete: ${files.size}/30${if (files.size < 3) " (añade ${3 - files.size} más para instalarlo)" else ""}"
        stickerList.removeAllViews()
        files.forEachIndexed { index, file ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val image = ImageView(this).apply {
                setImageBitmap(BitmapFactory.decodeFile(file.absolutePath))
                scaleType = ImageView.ScaleType.FIT_CENTER
                contentDescription = "Sticker ${index + 1}"
            }
            row.addView(image, LinearLayout.LayoutParams(64, 64))
            row.addView(TextView(this).apply {
                text = "Sticker ${index + 1} · ${file.length() / 1024} KB"
                textSize = 14f
                setTextColor(0xFF374151.toInt())
                setPadding(10, 0, 0, 0)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(Button(this).apply {
                text = "Quitar"
                setOnClickListener {
                    file.delete()
                    val remaining = stickerFiles()
                    remaining.forEachIndexed { i, f ->
                        val renamed = File(f.parentFile, "sticker_${(i + 1).toString().padStart(2, '0')}.webp")
                        if (f.name != renamed.name) f.renameTo(renamed)
                    }
                    val first = stickerFiles().firstOrNull()
                    if (first != null) updateTrayIcon(first) else File(ensureStickerDir(), "tray.png").delete()
                    refreshStickerList()
                    status.text = "Sticker eliminado."
                }
            })
            stickerList.addView(row)
        }
    }

    private fun addPackToWhatsApp() {
        val files = stickerFiles()
        if (files.size !in 3..30) {
            toast("WhatsApp requiere entre 3 y 30 stickers por paquete.")
            return
        }
        if (!File(ensureStickerDir(), "tray.png").exists()) updateTrayIcon(files.first())
        val intent = Intent("com.whatsapp.intent.action.ENABLE_STICKER_PACK").apply {
            putExtra("sticker_pack_id", PACK_ID)
            putExtra("sticker_pack_authority", StickerContentProvider.AUTHORITY)
            putExtra("sticker_pack_name", PACK_NAME)
        }
        try {
            startActivityForResult(intent, 200)
        } catch (_: ActivityNotFoundException) {
            val businessIntent = Intent("com.whatsapp.w4b.intent.action.ENABLE_STICKER_PACK").apply {
                putExtra("sticker_pack_id", PACK_ID)
                putExtra("sticker_pack_authority", StickerContentProvider.AUTHORITY)
                putExtra("sticker_pack_name", PACK_NAME)
            }
            try { startActivityForResult(businessIntent, 200) }
            catch (_: ActivityNotFoundException) { toast("No he encontrado WhatsApp compatible en este móvil.") }
        } catch (e: Exception) {
            toast("No se pudo abrir WhatsApp: ${e.localizedMessage ?: "error"}")
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        const val PACK_ID = "tik2whats_static_pack"
        const val PACK_NAME = "Tik2Whats"
    }
}
