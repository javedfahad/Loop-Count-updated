package com.example.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.File
import java.io.FileOutputStream

object QRCodeHelper {

    const val UPI_ID = "7654992592@kotakbank"
    const val PAYEE_NAME = "FAHAD JAVED"
    const val UPI_URI = "upi://pay?pa=7654992592@kotakbank&pn=FAHAD%20JAVED&cu=INR"

    /**
     * Generates a high-contrast QR code bitmap for the UPI URI with high error correction.
     */
    fun generateUpiQrBitmap(
        content: String = UPI_URI,
        size: Int = 800,
        withGPayBadge: Boolean = true
    ): Bitmap {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H,
            EncodeHintType.MARGIN to 1
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }

        if (withGPayBadge) {
            drawGPayCenterBadge(bitmap)
        }

        return bitmap
    }

    /**
     * Draws the Google Pay center badge icon over the QR code.
     */
    private fun drawGPayCenterBadge(bitmap: Bitmap) {
        val canvas = Canvas(bitmap)
        val width = bitmap.width
        val height = bitmap.height
        val cx = width / 2f
        val cy = height / 2f
        val badgeRadius = width * 0.11f

        // White circular background with subtle shadow border
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E0E0E0")
            style = Paint.Style.STROKE
            strokeWidth = width * 0.008f
        }
        canvas.drawCircle(cx, cy, badgeRadius, bgPaint)
        canvas.drawCircle(cx, cy, badgeRadius, borderPaint)

        // Draw Google Pay colored loop badge icon (Blue, Red, Yellow, Green)
        val pillWidth = badgeRadius * 1.05f
        val pillHeight = badgeRadius * 0.32f
        val radius = pillHeight / 2f

        canvas.save()
        canvas.rotate(-35f, cx, cy)

        // Upper pill (Blue to Red)
        val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#4285F4")
            style = Paint.Style.FILL
        }
        val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#EA4335")
            style = Paint.Style.FILL
        }
        val yellowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FBBC04")
            style = Paint.Style.FILL
        }
        val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#34A853")
            style = Paint.Style.FILL
        }

        val pillRect1 = RectF(cx - pillWidth / 2f, cy - pillHeight - 2f, cx + pillWidth / 2f, cy - 2f)
        val pillRect2 = RectF(cx - pillWidth / 2f, cy + 2f, cx + pillWidth / 2f, cy + pillHeight + 2f)

        canvas.drawRoundRect(pillRect1, radius, radius, bluePaint)
        canvas.drawRoundRect(pillRect2, radius, radius, yellowPaint)

        // Accent rounded tips
        val accentR1 = RectF(cx + pillWidth / 4f, cy - pillHeight - 2f, cx + pillWidth / 2f, cy - 2f)
        val accentR2 = RectF(cx - pillWidth / 2f, cy + 2f, cx - pillWidth / 4f, cy + pillHeight + 2f)
        canvas.drawRoundRect(accentR1, radius, radius, redPaint)
        canvas.drawRoundRect(accentR2, radius, radius, greenPaint)

        canvas.restore()
    }

    /**
     * Renders a full branded payment card image matching the user's Google Pay UPI card design.
     */
    fun renderFullPaymentCardBitmap(
        qrSize: Int = 600,
        upiId: String = UPI_ID
    ): Bitmap {
        val padding = 60
        val cardWidth = qrSize + padding * 2
        val cardHeight = qrSize + padding * 2 + 220

        val cardBitmap = Bitmap.createBitmap(cardWidth, cardHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(cardBitmap)

        // Soft white card background
        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
        }
        val cardRect = RectF(0f, 0f, cardWidth.toFloat(), cardHeight.toFloat())
        val cornerRadius = 36f
        canvas.drawRoundRect(cardRect, cornerRadius, cornerRadius, cardPaint)

        // Draw QR Code in center
        val qrBitmap = generateUpiQrBitmap(UPI_URI, qrSize, withGPayBadge = true)
        canvas.drawBitmap(qrBitmap, padding.toFloat(), padding.toFloat(), null)

        // Text "UPI ID: faahdmallick-1@okhdfcbank"
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#202124")
            textSize = 34f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val textY1 = padding + qrSize + 85f
        canvas.drawText("UPI ID: $upiId", cardWidth / 2f, textY1, textPaint)

        // Subtext "Scan to pay with any UPI app"
        val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#5F6368")
            textSize = 28f
            textAlign = Paint.Align.CENTER
        }
        val textY2 = textY1 + 55f
        canvas.drawText("Scan to pay with any UPI app", cardWidth / 2f, textY2, subTextPaint)

        return cardBitmap
    }

    /**
     * Retrieves the official Kotak 811 QR bitmap matching the user-uploaded card.
     */
    fun getKotakQrBitmap(context: Context): Bitmap {
        return try {
            BitmapFactory.decodeResource(context.resources, com.example.R.drawable.kotak_upi_qr)
                ?: renderFullPaymentCardBitmap()
        } catch (e: Exception) {
            renderFullPaymentCardBitmap()
        }
    }

    /**
     * Saves the QR code card into the device's Pictures gallery using MediaStore.
     */
    fun saveQrToGallery(context: Context): Boolean {
        return try {
            val bitmap = getKotakQrBitmap(context)
            val fileName = "Loopify_Kotak_UPI_QR_${System.currentTimeMillis()}.png"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Loopify")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(uri, values, null, null)
                    true
                } else {
                    false
                }
            } else {
                @Suppress("DEPRECATION")
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val loopifyDir = File(picturesDir, "Loopify").apply { mkdirs() }
                val file = File(loopifyDir, fileName)
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DATA, file.absolutePath)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                }
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                true
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Shares the QR code image and UPI information via Android's native share sheet.
     */
    fun shareQrCode(context: Context) {
        try {
            val bitmap = getKotakQrBitmap(context)
            val cacheFolder = File(context.cacheDir, "images").apply { mkdirs() }
            val file = File(cacheFolder, "loopify_kotak_support_qr.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Support Loopify Music independent development!\nUPI ID: $UPI_ID\nScan to pay with any UPI app (GPay, PhonePe, Paytm)."
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Loopify Music QR Code")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Could not share QR: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Launches an external UPI app intent directly on the user's phone.
     */
    fun openUpiIntent(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(UPI_URI)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
