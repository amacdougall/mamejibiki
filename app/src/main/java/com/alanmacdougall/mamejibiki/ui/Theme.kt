package com.alanmacdougall.mamejibiki.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.intl.LocaleList

/** Japanese locale on every text style, so Han-unified kanji render in their Japanese shapes. */
private fun TextStyle.japanese() = copy(localeList = LocaleList("ja"))

private val japaneseTypography = Typography().run {
    copy(
        displayLarge = displayLarge.japanese(), displayMedium = displayMedium.japanese(), displaySmall = displaySmall.japanese(),
        headlineLarge = headlineLarge.japanese(), headlineMedium = headlineMedium.japanese(), headlineSmall = headlineSmall.japanese(),
        titleLarge = titleLarge.japanese(), titleMedium = titleMedium.japanese(), titleSmall = titleSmall.japanese(),
        bodyLarge = bodyLarge.japanese(), bodyMedium = bodyMedium.japanese(), bodySmall = bodySmall.japanese(),
        labelLarge = labelLarge.japanese(), labelMedium = labelMedium.japanese(), labelSmall = labelSmall.japanese(),
    )
}

@Composable
fun MamejibikiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        typography = japaneseTypography,
        content = content,
    )
}
