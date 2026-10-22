package com.security.aetherdns

import androidx.compose.material3.Typography
import androidx.compose.ui.unit.TextUnitType
import com.security.aetherdns.ui.theme.Typography as AppTypography
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresi v1.10–v1.13: lineHeight `em` di tema membuat TextField berlabel crash ("Cannot perform operation for Em
 * and Sp") karena Material me-lerp gaya label antara bodyLarge dan bodySmall. Semua ukuran teks harus `sp`.
 */
class TextFieldThemeTest {

    private fun Typography.allStyles() = listOf(
        displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium, headlineSmall,
        titleLarge, titleMedium, titleSmall, bodyLarge, bodyMedium, bodySmall, labelLarge, labelMedium, labelSmall
    )

    @Test
    fun `theme typography uses only sp units`() {
        AppTypography.allStyles().forEach { style ->
            listOf(style.fontSize, style.lineHeight).filter { it.type != TextUnitType.Unspecified }.forEach { unit ->
                assertTrue("Satuan teks harus sp, bukan $unit", unit.type == TextUnitType.Sp)
            }
        }
    }
}
