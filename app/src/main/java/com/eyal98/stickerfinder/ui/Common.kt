package com.eyal98.stickerfinder.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eyal98.stickerfinder.R

/** The app logo (the launcher icon's layers), round. */
@Composable
fun AppLogo(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape)) {
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
        // The foreground is drawn for a 108dp canvas of which launchers show the middle 72dp:
        // scale it up the same way.
        Image(
            painterResource(R.drawable.ic_launcher_foreground),
            null,
            Modifier.fillMaxSize().graphicsLayer(scaleX = 1.5f, scaleY = 1.5f),
        )
    }
}

/** The mascot, a lady elephant lifting a sticker with her trunk. */
@Composable
fun Mascot(size: Dp, modifier: Modifier = Modifier) {
    Image(
        painterResource(R.drawable.mascot),
        contentDescription = stringResource(R.string.mascot_description),
        modifier = modifier.size(size),
    )
}

/** A screen's top row: back arrow and title. */
@Composable
fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 4.dp),
        )
        actions()
    }
}
