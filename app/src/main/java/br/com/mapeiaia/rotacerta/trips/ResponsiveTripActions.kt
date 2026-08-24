package br.com.mapeiaia.rotacerta.trips

import android.widget.Toast
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

data class ResponsiveTripAction(
    val label: String,
    val outlined: Boolean = true,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

/** Shared phone-safe action renderer. No label is allowed to wrap into a tall pill. */
@Composable
fun ResponsiveTripActions(actions: List<ResponsiveTripAction>, modifier: Modifier = Modifier) {
    if (actions.isEmpty()) return
    val context = LocalContext.current
    val agendaToolbar = actions.any { action ->
        action.label.contains("Sincronizar BlaBlaCar", ignoreCase = true) ||
            action.label.contains("Fechar sincronização", ignoreCase = true)
    }
    var showPublicSearch by remember { mutableStateOf(false) }
    val publicStore = remember(context, agendaToolbar) { if (agendaToolbar) BlaBlaPublicSearchStore(context) else null }
    var publicResponse by remember(context, agendaToolbar) { mutableStateOf(publicStore?.lastResponse()) }
    val agendaTrips = remember(context, agendaToolbar, showPublicSearch) {
        if (agendaToolbar && showPublicSearch) TripStore(context).trips() else emptyList()
    }
    val effectiveActions = if (agendaToolbar) {
        actions + ResponsiveTripAction(
            label = if (showPublicSearch) "Fechar consulta pública" else "Consulta pública",
            onClick = { showPublicSearch = !showPublicSearch },
        )
    } else {
        actions
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < 360.dp || effectiveActions.size > 2
            if (narrow) {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                    effectiveActions.forEach { action ->
                        if (action.outlined) {
                            OutlinedButton(action.onClick, enabled = action.enabled, modifier = Modifier.fillMaxWidth()) { Text(action.label, maxLines = 1) }
                        } else {
                            Button(action.onClick, enabled = action.enabled, modifier = Modifier.fillMaxWidth()) { Text(action.label, maxLines = 1) }
                        }
                    }
                }
            } else {
                val gap = 8.dp
                val width = (maxWidth - gap * (effectiveActions.size - 1)) / effectiveActions.size
                Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                    effectiveActions.forEach { action ->
                        if (action.outlined) {
                            OutlinedButton(action.onClick, enabled = action.enabled, modifier = Modifier.width(width)) { Text(action.label, maxLines = 1) }
                        } else {
                            Button(action.onClick, enabled = action.enabled, modifier = Modifier.width(width)) { Text(action.label, maxLines = 1) }
                        }
                    }
                }
            }
        }

        if (agendaToolbar && showPublicSearch) {
            BlaBlaPublicSearchPanel(
                trips = agendaTrips,
                currentResponse = publicResponse,
                onResult = { publicResponse = it },
                onChanged = { message ->
                    if (message.isNotBlank()) Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                },
            )
            publicResponse?.let { response ->
                BlaBlaPublicSearchTimelineResults(response = response, trips = agendaTrips)
            }
        }
    }
}
