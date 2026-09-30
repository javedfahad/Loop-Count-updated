package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import com.example.transfer.ShareToPayload
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Generates local, offline QR codes for the Share To feature using ZXing Core.
 * Ensures high contrast, standard quiet zone/margin, and fast offline rendering.
 */
object ShareToQrHelper {

    /**
     * Generates a high-resolution, high-contrast QR code bitmap for the given ShareToPayload.
     * Fully offline and self-contained.
     */
    fun generateShareToQrBitmap(
        payload: ShareToPayload,
        size: Int = 700
    ): Bitmap? {
        val payloadString = ShareToPayload.toPayloadString(payload)
        return generateRawQrBitmap(payloadString, size)
    }

    /**
     * Generates a QR code bitmap from an arbitrary string using ZXing.
     */
    fun generateRawQrBitmap(
        content: String,
        size: Int = 700
    ): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 2, // Standard quiet zone
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )

            val bitMatrix = QRCodeWriter().encode(
                content,
                BarcodeFormat.QR_CODE,
                size,
                size,
                hints
            )

            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)

            for (y in 0 until height) {
                val offset = y * width
                for (x in 0 until width) {
                    pixels[offset + x] = if (bitMatrix[x, y]) Color.BLACK else Color.WHITE
                }
            }

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
