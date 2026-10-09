package com.luntik.wallet

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AnimatedVisibilityScope.EndPanel(box: BoxScope, modifier: Modifier, content: @Composable () -> Unit) {
    box.run { androidx.compose.foundation.layout.Box(modifier) { content() } }
}
