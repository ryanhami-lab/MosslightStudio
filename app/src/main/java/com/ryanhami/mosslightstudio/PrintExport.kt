package com.ryanhami.mosslightstudio

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.FileProvider
import com.ryanhami.mosslightstudio.core.PrintDesign
import com.ryanhami.mosslightstudio.core.PrintPixels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PrintExport {
    suspend fun prepare(context: Context, design: PrintDesign): Intent = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "prints").apply { check(mkdirs() || isDirectory) }
        // Cache URIs are temporary. Keep recent exports available to their chosen recipient apps.
        directory.listFiles()?.filter { it.isFile && it.name.startsWith("studio-") }
            ?.sortedByDescending { it.lastModified() }?.drop(31)?.forEach { it.delete() }
        val output = File.createTempFile("studio-", ".png", directory)
        val bitmap = render(design)
        try {
            output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } catch (failure: Exception) {
            output.delete()
            throw failure
        } finally { bitmap.recycle() }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.prints", output)
        Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri("Mosslight print", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun render(design: PrintDesign): Bitmap {
        val scale = 4
        val bitmap = Bitmap.createBitmap(PrintPixels.WIDTH * scale, PrintPixels.HEIGHT * scale, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { scale(scale.toFloat(), scale.toFloat()) }
        val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
        for (block in PrintPixels.compose(design)) {
            paint.color = block.argb
            canvas.drawRect(block.x.toFloat(), block.y.toFloat(), (block.x + block.width).toFloat(), (block.y + block.height).toFloat(), paint)
        }
        return bitmap
    }
}

