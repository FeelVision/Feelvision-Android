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

            rotateNV21_180_InPlace(nv21Data, width, height)

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

    private fun rotateNV21_180_InPlace(data: ByteArray, width: Int, height: Int) {
        val size = width * height
        // Rotate Y plane
        for (i in 0 until size / 2) {
            val temp = data[i]
            data[i] = data[size - 1 - i]
            data[size - 1 - i] = temp
        }
        // Rotate UV plane (V and U are interleaved)
        // Total UV size is size / 2.
        var left = size
        var right = size + (size / 2) - 2
        while (left < right) {
            val tempV = data[left]
            val tempU = data[left + 1]
            data[left] = data[right]
            data[left + 1] = data[right + 1]
            data[right] = tempV
            data[right + 1] = tempU
            left += 2
            right -= 2
        }
    }
}
