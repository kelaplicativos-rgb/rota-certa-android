package br.com.mapeiaia.rotacerta.trips

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AgendaPullRefreshGesture0388InstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun validSwipeDownRoutesExactlyOnceThroughFullRefreshCallback() {
        var refreshRunning by mutableStateOf(false)
        var onRefreshCalls = 0
        var userSyncAllEvents = 0
        var pullRequestedEvents = 0

        composeRule.setContent {
            MaterialTheme {
                TimelineRefreshGestureSurface0388(
                    modifier = Modifier.fillMaxSize().testTag("surface"),
                    refreshing = refreshRunning,
                    canRefreshAtGestureStart = { true },
                    onRefresh = {
                        if (shouldStartAgendaFullRefresh0388(true, refreshRunning)) {
                            onRefreshCalls++
                            userSyncAllEvents++
                            pullRequestedEvents++
                            refreshRunning = true
                        }
                    },
                ) {
                    Spacer(Modifier.fillMaxSize())
                }
            }
        }

        composeRule.onNodeWithTag("surface").performTouchInput { swipeDown() }
        composeRule.waitForIdle()

        assertEquals(1, onRefreshCalls)
        assertEquals(1, userSyncAllEvents)
        assertEquals(1, pullRequestedEvents)
    }

    @Test
    fun toolbarButtonTapExecutesButtonOnceAndNeverRefreshes() {
        var buttonClicks = 0
        var refreshCalls = 0
        composeRule.setContent {
            MaterialTheme {
                TimelineRefreshGestureSurface0388(
                    modifier = Modifier.fillMaxSize().testTag("surface"),
                    refreshing = false,
                    canRefreshAtGestureStart = { true },
                    onRefresh = { refreshCalls++ },
                ) {
                    Button(
                        modifier = Modifier.testTag("button"),
                        onClick = { buttonClicks++ },
                    ) { Text("Passageiros") }
                }
            }
        }

        composeRule.onNodeWithTag("button").performClick()
        composeRule.waitForIdle()

        assertEquals(1, buttonClicks)
        assertEquals(0, refreshCalls)
    }

    @Test
    fun cardTapReachesCardCallbackWithoutRefresh() {
        var cardClicks = 0
        var refreshCalls = 0
        composeRule.setContent {
            MaterialTheme {
                TimelineRefreshGestureSurface0388(
                    modifier = Modifier.fillMaxSize().testTag("surface"),
                    refreshing = false,
                    canRefreshAtGestureStart = { true },
                    onRefresh = { refreshCalls++ },
                ) {
                    Box(
                        Modifier
                            .testTag("card")
                            .height(120.dp)
                            .clickable { cardClicks++ },
                    ) {
                        Text("Card")
                    }
                }
            }
        }

        composeRule.onNodeWithTag("card").performClick()
        composeRule.waitForIdle()

        assertEquals(1, cardClicks)
        assertEquals(0, refreshCalls)
    }

    @Test
    fun searchFieldAcceptsTapAndTypingWithoutRefresh() {
        var refreshCalls = 0
        composeRule.setContent {
            MaterialTheme {
                var query by remember { mutableStateOf("") }
                TimelineRefreshGestureSurface0388(
                    modifier = Modifier.fillMaxSize().testTag("surface"),
                    refreshing = false,
                    canRefreshAtGestureStart = { true },
                    onRefresh = { refreshCalls++ },
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.testTag("search"),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("search").performClick()
        composeRule.onNodeWithTag("search").performTextInput("abc")
        composeRule.onNodeWithTag("search").assertTextContains("abc")
        assertEquals(0, refreshCalls)
    }

    @Test
    fun normalListScrollStartedAwayFromTopNeverTurnsIntoRefreshMidGesture() {
        var refreshCalls = 0
        lateinit var listState: LazyListState
        composeRule.setContent {
            MaterialTheme {
                val state = rememberLazyListState()
                listState = state
                TimelineRefreshGestureSurface0388(
                    modifier = Modifier.fillMaxSize().testTag("surface"),
                    refreshing = false,
                    canRefreshAtGestureStart = { !state.canScrollBackward },
                    onRefresh = { refreshCalls++ },
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().testTag("list"),
                        state = state,
                    ) {
                        items((0 until 80).toList()) { index ->
                            Text("item-$index", modifier = Modifier.height(56.dp))
                        }
                    }
                }
            }
        }

        composeRule.onNodeWithTag("list").performTouchInput { swipeUp() }
        composeRule.waitForIdle()
        composeRule.runOnIdle { assertTrue(listState.canScrollBackward) }

        composeRule.onNodeWithTag("list").performTouchInput { swipeDown() }
        composeRule.waitForIdle()

        assertEquals(0, refreshCalls)
    }

    @Test
    fun secondGestureWhileRefreshRunningDoesNotStartConcurrentCycle() {
        var refreshRunning by mutableStateOf(false)
        var refreshCalls = 0
        composeRule.setContent {
            MaterialTheme {
                TimelineRefreshGestureSurface0388(
                    modifier = Modifier.fillMaxSize().testTag("surface"),
                    refreshing = refreshRunning,
                    canRefreshAtGestureStart = { true },
                    onRefresh = {
                        refreshCalls++
                        refreshRunning = true
                    },
                ) {
                    Spacer(Modifier.fillMaxSize())
                }
            }
        }

        composeRule.onNodeWithTag("surface").performTouchInput { swipeDown() }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("surface").performTouchInput { swipeDown() }
        composeRule.waitForIdle()

        assertEquals(1, refreshCalls)
    }

    @Test
    fun emptyTimelineSurfaceStillAcceptsOneValidPull() {
        var refreshCalls = 0
        composeRule.setContent {
            MaterialTheme {
                TimelineRefreshGestureSurface0388(
                    modifier = Modifier.fillMaxSize().testTag("empty"),
                    refreshing = false,
                    canRefreshAtGestureStart = { true },
                    onRefresh = { refreshCalls++ },
                ) {
                    Text("Nenhuma viagem sincronizada.")
                }
            }
        }

        composeRule.onNodeWithTag("empty").performTouchInput { swipeDown() }
        composeRule.waitForIdle()

        assertEquals(1, refreshCalls)
    }

    @Test
    fun movementInsideTouchSlopDoesNotRefresh() {
        var refreshCalls = 0
        composeRule.setContent {
            MaterialTheme {
                TimelineRefreshGestureSurface0388(
                    modifier = Modifier.fillMaxSize().testTag("surface"),
                    refreshing = false,
                    canRefreshAtGestureStart = { true },
                    onRefresh = { refreshCalls++ },
                ) {
                    Spacer(Modifier.fillMaxSize())
                }
            }
        }

        composeRule.onNodeWithTag("surface").performTouchInput {
            down(center)
            moveBy(Offset(0f, 1f))
            up()
        }
        composeRule.waitForIdle()

        assertEquals(0, refreshCalls)
    }

    @Test
    fun upwardHorizontalAndHorizontalDominantDiagonalDoNotRefresh() {
        var refreshCalls = 0
        composeRule.setContent {
            MaterialTheme {
                TimelineRefreshGestureSurface0388(
                    modifier = Modifier.fillMaxSize().testTag("surface"),
                    refreshing = false,
                    canRefreshAtGestureStart = { true },
                    onRefresh = { refreshCalls++ },
                ) {
                    Spacer(Modifier.fillMaxSize())
                }
            }
        }

        composeRule.onNodeWithTag("surface").performTouchInput { swipeUp() }
        composeRule.onNodeWithTag("surface").performTouchInput {
            swipe(
                start = center - Offset(220f, 0f),
                end = center + Offset(220f, 0f),
                durationMillis = 200,
            )
        }
        composeRule.onNodeWithTag("surface").performTouchInput {
            swipe(
                start = center - Offset(220f, 30f),
                end = center + Offset(220f, 30f),
                durationMillis = 200,
            )
        }
        composeRule.waitForIdle()

        assertEquals(0, refreshCalls)
    }
}
