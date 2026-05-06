package com.feelvision.luckfox

import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.util.Log
import java.io.ByteArrayOutputStream

object ImageConverter {

    private const val TAG = "ImageConverter"
    private const val JPEG_QUALITY = 92

    fun nv21ToJpeg(
        nv21Data: ByteArray,
        width: Int,
        height: Int
    ): ByteArray? {
        return try {
            val expectedSize = width * height * 3 / 2

            if (nv21Data.size < expectedSize) {
                Log.e(TAG, "BUFFER UNDERSIZE: expected $expectedSize, got ${nv21Data.size}")
                return null
            }

            val yuvImage = YuvImage(
                nv21Data,
                ImageFormat.NV21,
                width,
                height,
                null
            )

            val out = ByteArrayOutputStream()
            val success = yuvImage.compressToJpeg(
                Rect(0, 0, width, height),
                JPEG_QUALITY,
                out
            )

            if (!success) {
                Log.e(TAG, "compressToJpeg() returned false!")
                return null
            }

            out.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Conversion exception", e)
            null
        }
    }
}
