package com.astratouch.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astratouch.app.data.prefs.PreferencesStore
import com.astratouch.app.data.repository.ProfileRepository
import com.astratouch.app.domain.model.AppPreferences
import com.astratouch.app.domain.model.ThemeConfig
import com.astratouch.app.domain.model.ThemePreset
import com.astratouch.app.ui.navigation.AstraNavGraph
import com.astratouch.app.ui.theme.AstraTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    preferencesStore: PreferencesStore,
    profileRepository: ProfileRepository,
) : ViewModel() {
    val preferences: StateFlow<AppPreferences> = preferencesStore.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppPreferences())

    val theme: StateFlow<ThemeConfig> = combine(
        preferencesStore.preferences,
        profileRepository.observeThemes(),
    ) { prefs, themes ->
        themes.firstOrNull { it.id == "theme-${prefs.activeProfileId}" }
            ?: themes.firstOrNull { it.presetId == ThemePreset.MIDNIGHT_OBSIDIAN.id }
            ?: ThemeConfig.fromPreset(ThemePreset.MIDNIGHT_OBSIDIAN)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        ThemeConfig.fromPreset(ThemePreset.MIDNIGHT_OBSIDIAN),
    )
}

@Composable
fun AstraTouchApp(initialRoute: String?, viewModel: AppViewModel = hiltViewModel()) {
    val theme by viewModel.theme.collectAsState()
    val prefs by viewModel.preferences.collectAsState()
    var startRoute by remember { mutableStateOf(initialRoute) }

    AstraTheme(theme = theme, reducedMotion = prefs.reduceMotion) {
        AstraNavGraph(startDestination = startRoute ?: com.astratouch.app.util.NavRoutes.SPLASH)
    }
}
