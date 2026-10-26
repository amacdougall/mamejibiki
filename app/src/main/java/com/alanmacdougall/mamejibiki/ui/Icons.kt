package com.alanmacdougall.mamejibiki.ui

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.vector.ImageVector

/** Material "content_paste", which isn't in material-icons-core. */
val PasteIcon: ImageVector = materialIcon(name = "Filled.ContentPaste") {
    materialPath {
        moveTo(19f, 2f); horizontalLineToRelative(-4.18f)
        curveTo(14.4f, 0.84f, 13.3f, 0f, 12f, 0f)
        curveToRelative(-1.3f, 0f, -2.4f, 0.84f, -2.82f, 2f)
        horizontalLineTo(5f)
        curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
        verticalLineToRelative(16f)
        curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
        horizontalLineToRelative(14f)
        curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
        verticalLineTo(4f)
        curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
        close()
        moveTo(12f, 2f)
        curveToRelative(0.55f, 0f, 1f, 0.45f, 1f, 1f)
        reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
        reflectiveCurveToRelative(-1f, -0.45f, -1f, -1f)
        reflectiveCurveToRelative(0.45f, -1f, 1f, -1f)
        close()
        moveTo(19f, 20f); horizontalLineTo(5f); verticalLineTo(4f)
        horizontalLineToRelative(2f); verticalLineToRelative(3f)
        horizontalLineToRelative(10f); verticalLineTo(4f)
        horizontalLineToRelative(2f); verticalLineToRelative(16f)
        close()
    }
}
