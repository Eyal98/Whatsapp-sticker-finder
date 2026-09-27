package com.eyal98.stickerfinder.ui

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.eyal98.stickerfinder.R
import com.eyal98.stickerfinder.keyboard.StickerKeyboardService

@Composable
fun KeyboardSetupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(isKeyboardEnabled(context)) }
    // Re-check when coming back from the system keyboard settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { enabled = isKeyboardEnabled(context) }

    BackHandler(onBack = onBack)
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenHeader(stringResource(R.string.keyboard_setup_title), onBack)
            Text(stringResource(R.string.keyboard_setup_body), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(if (enabled) R.string.keyboard_status_on else R.string.keyboard_status_off),
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            if (!enabled) {
                Button(onClick = { openKeyboardSettings(context) }) { Text(stringResource(R.string.keyboard_enable)) }
                Text(stringResource(R.string.keyboard_warning), style = MaterialTheme.typography.bodySmall)
            } else {
                OutlinedButton(onClick = { showPicker(context) }) { Text(stringResource(R.string.keyboard_choose)) }
            }
            Text(stringResource(R.string.keyboard_how_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.keyboard_how), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.keyboard_privacy), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun isKeyboardEnabled(context: Context): Boolean {
    val ours = ComponentName(context, StickerKeyboardService::class.java)
    return context.getSystemService(InputMethodManager::class.java)
        .enabledInputMethodList
        .any { it.component == ours }
}

private fun openKeyboardSettings(context: Context) {
    try {
        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

private fun showPicker(context: Context) {
    context.getSystemService(InputMethodManager::class.java).showInputMethodPicker()
}
