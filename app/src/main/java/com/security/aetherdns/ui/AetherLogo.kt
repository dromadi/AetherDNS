package com.security.aetherdns.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.security.aetherdns.R
import com.security.aetherdns.ui.theme.BrandGradientColors

/** Logo aplikasi (latar gradien + foreground ikon launcher) agar UI dan ikon tampil seragam. */
@Composable
fun AetherLogo(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(BrandGradientColors)),
        contentAlignment = Alignment.Center
    ) {
        // Foreground adaptive icon berukuran 108dp dengan zona aman 66dp: diperbesar agar perisai mengisi logo
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * (108f / 66f))
        )
    }
}

/** Latar gradien brand penuh untuk permukaan besar (kartu status aktif). */
fun Modifier.brandGradient(): Modifier = this.background(Brush.linearGradient(BrandGradientColors))

