package br.com.mapeiaia.rotacerta.trips

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
    ASSISTANT("Assistente Rota Certa"),
    AUTOMATIC_SYNC("BlaBlaCar"),
    SCRIPTS("Scripts"),
    PUBLIC_SEARCH("Consulta pública"),
    PASSENGERS("Passageiros"),
    INTEGRATIONS("Integrações"),
    APP_SETTINGS("Configurações"),
}

enum class AgendaTimelineCommand0396 {
    ADD_PASSENGER,
    TOGGLE_ARCHIVED,
    TOGGLE_SYNC_PENDING,
    DOWNLOAD_TIMELINE,
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
    publicAgendaEnabled: Boolean,
    onOpenPublicAgenda: () -> Unit,
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
                    Text("Rota Certa", style = MaterialTheme.typography.titleLarge)
                }
                HorizontalDivider()
                listOf(
                    AgendaRootSection0396.ALL_TRIPS,
                    AgendaRootSection0396.AUTOMATIC_SYNC,
                    AgendaRootSection0396.SCRIPTS,
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
                NavigationDrawerItem(
                    label = { Text("Abrir Agenda Pública", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    selected = false,
                    onClick = {
                        if (publicAgendaEnabled) {
                            onOpenPublicAgenda()
                            scope.launch { drawerState.close() }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                listOf(
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
    notificationUnreadCount: Int = 0,
    onNotificationsClick: (() -> Unit)? = null,
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
                .statusBarsPadding()
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
                        "Rota Certa",
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
            val unread = notificationUnreadCount.coerceAtLeast(0)
            val notificationsDescription = if (unread > 0) {
                "Notificações, $unread não lidas"
            } else {
                "Notificações"
            }
            IconButton(
                onClick = { onNotificationsClick?.invoke() },
                enabled = onNotificationsClick != null,
                modifier = Modifier.semantics { contentDescription = notificationsDescription },
            ) {
                BadgedBox(
                    badge = {
                        if (unread > 0) {
                            Badge {
                                Text(if (unread > 99) "99+" else unread.toString())
                            }
                        }
                    },
                ) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = null,
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
