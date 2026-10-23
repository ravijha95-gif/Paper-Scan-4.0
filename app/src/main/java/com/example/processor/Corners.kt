package com.example.processor

import android.graphics.PointF

data class CornerPoint(
    val x: Float,
    val y: Float
) {
    fun toPointF(): PointF = PointF(x, y)
}

data class Corners(
    val topLeft: CornerPoint,
    val topRight: CornerPoint,
    val bottomRight: CornerPoint,
    val bottomLeft: CornerPoint,
    val confidence: Float = 1.0f
) {
    fun toFloatArray(): FloatArray {
        return floatArrayOf(
            topLeft.x, topLeft.y,
            topRight.x, topRight.y,
            bottomRight.x, bottomRight.y,
            bottomLeft.x, bottomLeft.y
        )
    }

    fun toNormalizedString(): String {
        return "${topLeft.x},${topLeft.y},${topRight.x},${topRight.y},${bottomRight.x},${bottomRight.y},${bottomLeft.x},${bottomLeft.y}"
    }

    companion object {
        fun defaultFull(width: Float, height: Float, insetRatio: Float = 0.05f): Corners {
            val insetX = width * insetRatio
            val insetY = height * insetRatio
            return Corners(
                topLeft = CornerPoint(insetX, insetY),
                topRight = CornerPoint(width - insetX, insetY),
                bottomRight = CornerPoint(width - insetX, height - insetY),
                bottomLeft = CornerPoint(insetX, height - insetY)
            )
        }

        fun fromNormalizedString(str: String, width: Float, height: Float): Corners? {
            if (str.isBlank()) return null
            return try {
                val parts = str.split(",").map { it.trim().toFloat() }
                if (parts.size == 8) {
                    Corners(
                        topLeft = CornerPoint(parts[0] * width, parts[1] * height),
                        topRight = CornerPoint(parts[2] * width, parts[3] * height),
                        bottomRight = CornerPoint(parts[4] * width, parts[5] * height),
                        bottomLeft = CornerPoint(parts[6] * width, parts[7] * height)
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}
