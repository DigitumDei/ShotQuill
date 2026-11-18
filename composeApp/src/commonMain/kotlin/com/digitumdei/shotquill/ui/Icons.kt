package com.digitumdei.shotquill.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The handful of icons the app needs, drawn as 24dp line icons so they share
 * one weight. Tint them with [androidx.compose.material3.Icon].
 */
object ShotQuillIcons {
    val Back: ImageVector by lazy {
        lineIcon("Back") {
            moveTo(19f, 12f)
            lineTo(5f, 12f)
            moveTo(11f, 6f)
            lineTo(5f, 12f)
            lineTo(11f, 18f)
        }
    }

    val Camera: ImageVector by lazy {
        lineIcon("Camera") {
            moveTo(4f, 8f)
            lineTo(7.5f, 8f)
            lineTo(9f, 5.5f)
            lineTo(15f, 5.5f)
            lineTo(16.5f, 8f)
            lineTo(20f, 8f)
            lineTo(20f, 19f)
            lineTo(4f, 19f)
            close()
            circle(12f, 13.2f, 3.4f)
        }
    }

    val Photos: ImageVector by lazy {
        lineIcon("Photos") {
            moveTo(4f, 5f)
            lineTo(20f, 5f)
            lineTo(20f, 19f)
            lineTo(4f, 19f)
            close()
            moveTo(4f, 16f)
            lineTo(9f, 11f)
            lineTo(13f, 15f)
            lineTo(16f, 12.5f)
            lineTo(20f, 16f)
            circle(15.5f, 8.8f, 1.3f)
        }
    }

    val Settings: ImageVector by lazy {
        lineIcon("Settings") {
            // Three sliders: quieter than a cog and closer to what the screen holds.
            moveTo(4f, 7f)
            lineTo(20f, 7f)
            moveTo(4f, 12f)
            lineTo(20f, 12f)
            moveTo(4f, 17f)
            lineTo(20f, 17f)
            circle(9f, 7f, 2f)
            circle(15f, 12f, 2f)
            circle(8f, 17f, 2f)
        }
    }

    val Share: ImageVector by lazy {
        lineIcon("Share") {
            moveTo(12f, 15f)
            lineTo(12f, 4f)
            moveTo(7.5f, 8.5f)
            lineTo(12f, 4f)
            lineTo(16.5f, 8.5f)
            moveTo(5f, 13f)
            lineTo(5f, 20f)
            lineTo(19f, 20f)
            lineTo(19f, 13f)
        }
    }

    val Delete: ImageVector by lazy {
        lineIcon("Delete") {
            moveTo(4.5f, 7f)
            lineTo(19.5f, 7f)
            moveTo(9.5f, 7f)
            lineTo(10f, 4.5f)
            lineTo(14f, 4.5f)
            lineTo(14.5f, 7f)
            moveTo(6.5f, 7f)
            lineTo(7.5f, 20f)
            lineTo(16.5f, 20f)
            lineTo(17.5f, 7f)
            moveTo(10.2f, 10.5f)
            lineTo(10.5f, 16.5f)
            moveTo(13.8f, 10.5f)
            lineTo(13.5f, 16.5f)
        }
    }

    val Add: ImageVector by lazy {
        lineIcon("Add") {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    }

    val Close: ImageVector by lazy {
        lineIcon("Close") {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }
    }

    val Check: ImageVector by lazy {
        lineIcon("Check") {
            moveTo(5f, 12.5f)
            lineTo(10f, 17.5f)
            lineTo(19f, 7f)
        }
    }

    private fun lineIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.75f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathFillType = PathFillType.NonZero,
                pathBuilder = block,
            )
        }.build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx + r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx - r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = cx + r, y1 = cy)
    }
}
