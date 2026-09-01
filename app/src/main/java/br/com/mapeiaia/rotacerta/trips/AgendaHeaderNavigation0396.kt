package br.com.mapeiaia.rotacerta.trips

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

internal enum class AgendaRootSection0396(val label: String) {
    ALL_TRIPS("Todas as viagens"),
    AUTOMATIC_SYNC("Sincronização automática"),
    PUBLIC_SEARCH("Consulta pública"),
    PASSENGERS("Passageiros"),
    INTEGRATIONS("Integrações"),
    APP_SETTINGS("Configurações"),
}

enum class AgendaTimelineCommand0396 {
    ADD_PASSENGER,
    TOGGLE_ARCHIVED,
}

internal data class AgendaHeaderAction0396(
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
internal fun AgendaModuleDrawer0396(
    currentSection: AgendaRootSection0396,
    onSelect: (AgendaRootSection0396) -> Unit,
    content: @Composable (openDrawer: () -> Unit) -> Unit,
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val openDrawer = {
        scope.launch { drawerState.open() }
        Unit
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                ) {
                    Text("Agenda de Viagens", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Navegação",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text(AgendaRootSection0396.ALL_TRIPS.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    selected = currentSection == AgendaRootSection0396.ALL_TRIPS,
                    onClick = {
                        onSelect(AgendaRootSection0396.ALL_TRIPS)
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                Text(
                    "Central do Rota Certa",
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOf(
                    AgendaRootSection0396.AUTOMATIC_SYNC,
                    AgendaRootSection0396.PUBLIC_SEARCH,
                    AgendaRootSection0396.PASSENGERS,
                    AgendaRootSection0396.INTEGRATIONS,
                    AgendaRootSection0396.APP_SETTINGS,
                ).forEach { section ->
                    NavigationDrawerItem(
                        label = { Text(section.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        selected = section == currentSection,
                        onClick = {
                            onSelect(section)
                            scope.launch { drawerState.close() }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                }
            }
        },
    ) {
        content(openDrawer)
    }
}

@Composable
internal fun AgendaModuleHeader0396(
    sectionLabel: String,
    root: Boolean,
    onNavigationClick: () -> Unit,
    overflowActions: List<AgendaHeaderAction0396>,
    modifier: Modifier = Modifier,
) {
    var overflowExpanded by remember { mutableStateOf(false) }
    val navigationDescription = if (root) {
        "Abrir navegação da Agenda de Viagens"
    } else {
        "Voltar para a tela anterior"
    }

    Surface(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 2.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onNavigationClick,
                modifier = Modifier.semantics { contentDescription = navigationDescription },
            ) {
                Text(
                    text = if (root) "☰" else "←",
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (root) {
                    Text(
                        "Agenda de Viagens",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        sectionLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Text(
                        sectionLabel,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(
                onClick = { overflowExpanded = true },
                enabled = overflowActions.isNotEmpty(),
                modifier = Modifier.semantics { contentDescription = "Mais ações desta tela" },
            ) {
                Text("⋮", style = MaterialTheme.typography.titleLarge)
            }
            DropdownMenu(
                expanded = overflowExpanded,
                onDismissRequest = { overflowExpanded = false },
            ) {
                overflowActions.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(action.label) },
                        enabled = action.enabled,
                        onClick = {
                            overflowExpanded = false
                            action.onClick()
                        },
                    )
                }
            }
        }
    }
}
