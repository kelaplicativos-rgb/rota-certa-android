package br.com.mapeiaia.rotacerta.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

data class ResponsiveTripAction(
    val label: String,
    val outlined: Boolean = true,
    val enabled: Boolean = true,
    val traceKey: String? = null,
    val onClick: () -> Unit,
)

/** Shared phone-safe action renderer. No label is allowed to wrap into a tall pill. */
@Composable
fun ResponsiveTripActions(actions: List<ResponsiveTripAction>, modifier: Modifier = Modifier) {
    if (actions.isEmpty()) return
    val context = LocalContext.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val narrow = maxWidth < 360.dp || actions.size > 2
        if (narrow) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                actions.forEach { action ->
                    val tracedClick = {
                        AgendaTrace.action(context, action.traceKey, action.label)
                        action.onClick()
                    }
                    if (action.outlined) {
                        OutlinedButton(tracedClick, enabled = action.enabled, modifier = Modifier.fillMaxWidth()) { Text(action.label, maxLines = 1) }
                    } else {
                        Button(tracedClick, enabled = action.enabled, modifier = Modifier.fillMaxWidth()) { Text(action.label, maxLines = 1) }
                    }
                }
            }
        } else {
            val gap = 8.dp
            val width = (maxWidth - gap * (actions.size - 1)) / actions.size
            Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                actions.forEach { action ->
                    val tracedClick = {
                        AgendaTrace.action(context, action.traceKey, action.label)
                        action.onClick()
                    }
                    if (action.outlined) {
                        OutlinedButton(tracedClick, enabled = action.enabled, modifier = Modifier.width(width)) { Text(action.label, maxLines = 1) }
                    } else {
                        Button(tracedClick, enabled = action.enabled, modifier = Modifier.width(width)) { Text(action.label, maxLines = 1) }
                    }
                }
            }
        }
    }
}
