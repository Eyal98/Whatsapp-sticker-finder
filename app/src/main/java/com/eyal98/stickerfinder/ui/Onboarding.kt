package com.eyal98.stickerfinder.ui

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.eyal98.stickerfinder.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Getting new users going, the friendly way: a "Getting started" checklist that ticks itself off,
 * one-time hints the first time a screen opens, and tips from Pili (the mascot) while stickers are
 * being read. What's done and what was dismissed is kept in app preferences; About → "Show tips
 * again" brings everything back.
 */
object Onboarding {

    /** The checklist, in the order a new user would naturally meet them. */
    enum class Step(@StringRes val title: Int, @StringRes val detail: Int) {
        SEARCH(R.string.step_search, R.string.step_search_detail),
        SEND(R.string.step_send, R.string.step_send_detail),
        DETAILS(R.string.step_details, R.string.step_details_detail),
        STAR(R.string.step_star, R.string.step_star_detail),
        KEYBOARD(R.string.step_keyboard, R.string.step_keyboard_detail),
        SMART(R.string.step_smart, R.string.step_smart_detail),
    }

    /** Screens that show a one-time hint. */
    enum class Hint(@StringRes val text: Int) {
        DETAILS(R.string.hint_details),
        KEYBOARD(R.string.hint_keyboard),
        SMART_SEARCH(R.string.hint_smart_search),
        PEOPLE(R.string.hint_people),
        SUGGESTIONS(R.string.hint_suggestions),
    }

    data class State(val done: Set<Step>, val checklistClosed: Boolean, val hintsSeen: Set<Hint>)

    private const val PREFS = "onboarding"
    private const val KEY_DONE = "done"
    private const val KEY_CLOSED = "checklist_closed"
    private const val KEY_HINTS = "hints_seen"

    private val state = MutableStateFlow<State?>(null)

    fun observe(context: Context): StateFlow<State?> {
        if (state.value == null) state.value = load(context)
        return state.asStateFlow()
    }

    fun complete(context: Context, step: Step) = change(context) { it.copy(done = it.done + step) }

    fun closeChecklist(context: Context) = change(context) { it.copy(checklistClosed = true) }

    fun seeHint(context: Context, hint: Hint) = change(context) { it.copy(hintsSeen = it.hintsSeen + hint) }

    /** About → "Show tips again". */
    fun reset(context: Context) = change(context) { State(it.done, checklistClosed = false, hintsSeen = emptySet()) }

    private fun change(context: Context, block: (State) -> State) {
        val current = state.value ?: load(context)
        val next = block(current)
        if (next == current) return
        state.update { next }
        prefs(context).edit {
            putStringSet(KEY_DONE, next.done.map { it.name }.toSet())
            putBoolean(KEY_CLOSED, next.checklistClosed)
            putStringSet(KEY_HINTS, next.hintsSeen.map { it.name }.toSet())
        }
    }

    private fun load(context: Context): State {
        val p = prefs(context)
        return State(
            done = p.getStringSet(KEY_DONE, emptySet()).orEmpty().mapNotNull { n -> Step.entries.firstOrNull { it.name == n } }.toSet(),
            checklistClosed = p.getBoolean(KEY_CLOSED, false),
            hintsSeen = p.getStringSet(KEY_HINTS, emptySet()).orEmpty().mapNotNull { n -> Hint.entries.firstOrNull { it.name == n } }.toSet(),
        )
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** A speech bubble from Pili: the mascot, a line of text, and an optional action. */
@Composable
fun PiliSays(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Mascot(56.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                action?.invoke()
            }
        }
    }
}

/** A hint shown the first time [hint]'s screen opens, until "Got it". */
@Composable
fun FirstTimeHint(hint: Onboarding.Hint, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by Onboarding.observe(context).collectAsState()
    val show = state?.let { hint !in it.hintsSeen } == true
    AnimatedVisibility(visible = show, modifier = modifier) {
        PiliSays(stringResource(hint.text)) {
            TextButton(onClick = { Onboarding.seeHint(context, hint) }) { Text(stringResource(R.string.hint_got_it)) }
        }
    }
}

/** Loading-screen style tips while stickers are being read; tap for another. */
@Composable
fun PiliTip(modifier: Modifier = Modifier) {
    val tips = TIPS
    var index by rememberSaveable { mutableIntStateOf(tips.indices.random()) }
    PiliSays(
        stringResource(R.string.tip_prefix) + " " + stringResource(tips[index]),
        modifier = modifier.clickable { index = (index + 1) % tips.size },
    )
}

/** The "Getting started" card on the main screen: ticks itself off, closes when done or dismissed. */
@Composable
fun GettingStarted(keyboardEnabled: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val state by Onboarding.observe(context).collectAsState()
    var open by rememberSaveable { mutableStateOf(true) }
    val s = state ?: return
    if (s.checklistClosed) return
    val done = if (keyboardEnabled) s.done + Onboarding.Step.KEYBOARD else s.done
    val steps = Onboarding.Step.entries
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { open = !open }) {
                Mascot(44.dp)
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        stringResource(if (done.size == steps.size) R.string.getting_started_done else R.string.getting_started_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        stringResource(R.string.getting_started_progress, done.size, steps.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                TextButton(onClick = { Onboarding.closeChecklist(context) }) { Text(stringResource(R.string.getting_started_close)) }
            }
            LinearProgressIndicator(progress = { done.size.toFloat() / steps.size }, modifier = Modifier.fillMaxWidth())
            if (open) {
                for (step in steps) {
                    val isDone = step in done
                    Column {
                        Text(
                            (if (isDone) "✓ " else "○ ") + stringResource(step.title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textDecoration = if (isDone) TextDecoration.LineThrough else null,
                        )
                        if (!isDone) {
                            Text(
                                stringResource(step.detail),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(start = 18.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val TIPS = listOf(
    R.string.tip_long_press,
    R.string.tip_keyboard,
    R.string.tip_hebrew_english,
    R.string.tip_meaning,
    R.string.tip_star,
    R.string.tip_learns,
    R.string.tip_charger,
    R.string.tip_people,
    R.string.tip_suggestions,
    R.string.tip_pack_tag,
    R.string.tip_backup,
    R.string.tip_chats,
    R.string.tip_offline,
    R.string.tip_description,
    R.string.tip_folders,
)
