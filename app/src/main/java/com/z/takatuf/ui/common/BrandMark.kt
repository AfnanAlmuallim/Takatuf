package com.z.takatuf.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.z.takatuf.R

/**
 * The logo's three figures. The navy figure disappears on a dark background, so in dark
 * theme the mark sits on a white disc, as it does on the splash screen.
 */
@Composable
fun BrandMark(modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val dark = isSystemInDarkTheme()
    Box(
        modifier = modifier
            .size(size)
            .then(if (dark) Modifier.background(Color.White, CircleShape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.takatuf_mark),
            contentDescription = null,
            modifier = Modifier
                .padding(if (dark) size * 0.16f else 0.dp)
                .size(size),
        )
    }
}
