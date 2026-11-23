package uk.rdfn.h.off.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import uk.rdfn.h.off.AppContainer
import uk.rdfn.h.off.R
import uk.rdfn.h.off.connection.ConnectionState
import uk.rdfn.h.off.ui.screens.HistoryScreen
import uk.rdfn.h.off.ui.screens.NowScreen
import uk.rdfn.h.off.ui.screens.ProgramDetailScreen
import uk.rdfn.h.off.ui.screens.ProgramsScreen
import uk.rdfn.h.off.ui.screens.SettingsScreen
import uk.rdfn.h.off.ui.screens.SignInScreen
import uk.rdfn.h.off.ui.screens.StartingScreen
import uk.rdfn.h.off.ui.screens.UnreachableScreen

/** The screens you can move between once connected. */
@Serializable
private data object HomeKey : NavKey

@Serializable
private data class ProgramKey(val programId: String, val programName: String) : NavKey

@Serializable
private data object SettingsKey : NavKey

/** The three tabs along the bottom. */
private enum class HomeTab(val title: String, val emoji: String) {
	NOW("Now", "🧺"),
	PROGRAMS("Programs", "📋"),
	HISTORY("History", "📊"),
}

/** Picks the screen for where the app is with h.on. */
@Composable
fun AppRoot(container: AppContainer, onSignIn: () -> Unit) {
	val repository = container.washerRepository
	val connection by repository.connection.collectAsStateWithLifecycle()
	val scope = rememberCoroutineScope()
	when (val state = connection) {
		ConnectionState.Starting -> StartingScreen()
		is ConnectionState.SignedOut -> SignInScreen(
			message = state.message,
			onSignIn = onSignIn,
			onTryDemo = { scope.launch { repository.startDemo() } },
		)
		is ConnectionState.Unreachable -> UnreachableScreen(
			message = state.message,
			onTryAgain = { scope.launch { repository.connect(retry = true) } },
			onSignOut = { scope.launch { repository.signOut() } },
		)
		is ConnectionState.Connected -> {
			AskForNotificationPermission()
			ConnectedApp(state, container)
		}
	}
}

/** Android 13 and later need permission for the "wash finished" alert. Asked once connected. */
@Composable
private fun AskForNotificationPermission() {
	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
	val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
	var hasAsked by rememberSaveable { mutableStateOf(false) }
	LaunchedEffect(Unit) {
		if (!hasAsked) {
			hasAsked = true
			// Android itself stops asking after you've said no twice.
			launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
		}
	}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConnectedApp(connected: ConnectionState.Connected, container: AppContainer) {
	val backStack = rememberNavBackStack(HomeKey)
	val scope = rememberCoroutineScope()
	var selectedTab by rememberSaveable { mutableStateOf(HomeTab.NOW) }

	NavDisplay(
		backStack = backStack,
		onBack = { backStack.removeLastOrNull() },
		entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator()),
		entryProvider = entryProvider {
			entry<HomeKey> {
				// Back from Programs or History goes to Now first, then closes the app.
				BackHandler(enabled = selectedTab != HomeTab.NOW) { selectedTab = HomeTab.NOW }
				Scaffold(
					modifier = Modifier.fillMaxSize(),
					topBar = { TopAppBar(title = { Text(if (selectedTab == HomeTab.NOW) "h.off" else selectedTab.title) }) },
					bottomBar = {
						NavigationBar {
							HomeTab.entries.forEach { tab ->
								NavigationBarItem(
									selected = selectedTab == tab,
									onClick = { selectedTab = tab },
									icon = { Text(tab.emoji, modifier = Modifier.clearAndSetSemantics { }) },
									label = { Text(tab.title) },
								)
							}
						}
					},
				) { innerPadding ->
					when (selectedTab) {
						HomeTab.NOW -> NowScreen(
							connected = connected,
							repository = container.washerRepository,
							onChooseProgram = { selectedTab = HomeTab.PROGRAMS },
							contentPadding = innerPadding,
						)
						HomeTab.PROGRAMS -> ProgramsScreen(
							washer = connected.washer,
							storage = container.storage,
							onOpenProgram = { program -> backStack.add(ProgramKey(program.id, program.displayName)) },
							contentPadding = innerPadding,
						)
						HomeTab.HISTORY -> HistoryScreen(
							connected = connected,
							storage = container.storage,
							onOpenSettings = { backStack.add(SettingsKey) },
							onSignOut = { scope.launch { container.washerRepository.signOut() } },
							contentPadding = innerPadding,
						)
					}
				}
			}
			entry<ProgramKey> { key ->
				var title by rememberSaveable { mutableStateOf(key.programName) }
				InnerScreen(title, onBack = { backStack.removeLastOrNull() }) { innerPadding ->
					ProgramDetailScreen(
						programId = key.programId,
						washer = connected.washer,
						repository = container.washerRepository,
						onStarted = {
							// Back to the Now screen to watch it start.
							backStack.removeAll { it != HomeKey }
							selectedTab = HomeTab.NOW
						},
						onTitleKnown = { title = it },
						contentPadding = innerPadding,
					)
				}
			}
			entry<SettingsKey> {
				InnerScreen("Settings and reports", onBack = { backStack.removeLastOrNull() }) { innerPadding ->
					SettingsScreen(connected, container, innerPadding)
				}
			}
		},
	)
}

/** A screen with a back arrow, opened on top of the tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InnerScreen(title: String, onBack: () -> Unit, content: @Composable (PaddingValues) -> Unit) {
	Scaffold(
		modifier = Modifier.fillMaxSize(),
		topBar = {
			TopAppBar(
				title = { Text(title) },
				navigationIcon = {
					IconButton(onClick = onBack) {
						Icon(painterResource(R.drawable.arrow_back), contentDescription = "Back")
					}
				},
			)
		},
		content = content,
	)
}
