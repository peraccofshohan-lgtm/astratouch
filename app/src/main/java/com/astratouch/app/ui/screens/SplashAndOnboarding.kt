package com.astratouch.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.astratouch.app.data.prefs.PreferencesStore
import com.astratouch.app.domain.model.AccessId
import com.astratouch.app.domain.model.AccessState
import com.astratouch.app.domain.model.PermissionSnapshot
import com.astratouch.app.permission.PermissionManager
import com.astratouch.app.ui.components.AstraCard
import com.astratouch.app.ui.components.AstraPrimaryButton
import com.astratouch.app.ui.components.AstraSecondaryButton
import com.astratouch.app.ui.components.StatusPill
import com.astratouch.app.ui.theme.LocalAstraColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val preferencesStore: PreferencesStore,
) : ViewModel() {
    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            preferencesStore.setOnboardingComplete(true)
            onDone()
        }
    }
}

@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel(),
) {
    val astra = LocalAstraColors.current
    val scale = remember { Animatable(0.6f) }
    val alpha = remember { Animatable(0f) }
    val ready by viewModel.ready.collectAsState()

    LaunchedEffect(Unit) {
        scale.animateTo(1f, tween(520))
        alpha.animateTo(1f, tween(400))
    }
    LaunchedEffect(ready) {
        if (ready) {
            delay(420)
            onFinished()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(astra.surface, astra.background),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(104.dp)
                    .scale(scale.value)
                    .alpha(alpha.value)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(astra.primary, astra.secondary))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(52.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "AstraTouch",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = astra.onBackground,
                modifier = Modifier.alpha(alpha.value),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Your Phone. Your Control.",
                style = MaterialTheme.typography.bodyMedium,
                color = astra.primary,
                modifier = Modifier.alpha(alpha.value),
            )
            Spacer(Modifier.height(32.dp))
            if (!ready) {
                LinearProgressIndicator(
                    modifier = Modifier.width(120.dp),
                    color = astra.primary,
                )
            }
        }
    }
}

@HiltViewModel
class SplashViewModel @Inject constructor(
    preferencesStore: PreferencesStore,
) : ViewModel() {
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        viewModelScope.launch {
            // Warm the preference flow so the first frame of the dashboard is
            // rendered with real data instead of a flash of defaults.
            kotlinx.coroutines.flow.first(preferencesStore.preferences)
            delay(120)
            _ready.value = true
        }
    }
}

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val astra = LocalAstraColors.current
    val pages = listOf(
        OnboardingPage(
            Icons.Filled.Star,
            "A bubble that does it all",
            "AstraTouch puts a floating bubble on top of any app. Tap it for a radial menu of actions, " +
                "drag it anywhere, and it snaps neatly to the screen edge.",
        ),
        OnboardingPage(
            Icons.Filled.Bolt,
            "Real device control",
            "Home, Back, Recents, notifications, Quick Settings, volume, brightness, torch, screenshots — " +
                "every action tells you honestly whether your device supports it.",
        ),
        OnboardingPage(
            Icons.Filled.Palette,
            "Yours to design",
            "Six profiles, five themes, eight radial layouts. Change shape, colour, opacity, gestures and " +
                "macros. Everything you build stays on this device.",
        ),
        OnboardingPage(
            Icons.Filled.Lock,
            "Private by architecture",
            "AstraTouch has no internet permission at all. No account, no ads, no analytics, no tracking. " +
                "Two Android permissions power the bubble — you grant them on the next screen.",
        ),
    )
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
        ) { index ->
            val page = pages[index]
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(astra.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(page.icon, contentDescription = null, tint = astra.primary, modifier = Modifier.size(46.dp))
                }
                Spacer(Modifier.height(28.dp))
                Text(
                    page.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = astra.onBackground,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    page.body,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = astra.onBackground.copy(alpha = 0.75f),
                )
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(pages.size) { i ->
                Box(
                    Modifier
                        .padding(4.dp)
                        .size(if (i == pager.currentPage) 22.dp else 8.dp, 8.dp)
                        .clip(CircleShape)
                        .background(if (i == pager.currentPage) astra.primary else astra.outline),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        val last = pager.currentPage == pages.lastIndex
        AstraPrimaryButton(
            text = if (last) "Continue to permissions" else "Next",
            onClick = {
                if (last) {
                    viewModel.complete(onFinished)
                } else {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!last) {
            Spacer(Modifier.height(8.dp))
            AstraSecondaryButton(
                text = "Skip",
                onClick = { viewModel.complete(onFinished) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun PermissionSetupScreen(
    onFinished: () -> Unit,
    viewModel: PermissionViewModel = hiltViewModel(),
) {
    val astra = LocalAstraColors.current
    val snapshot by viewModel.snapshot.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text(
            "Set up AstraTouch",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = astra.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "AstraTouch asks for each access only when you reach the feature that needs it. " +
                "Everything below is optional except the overlay permission — without it there is no bubble.",
            style = MaterialTheme.typography.bodyMedium,
            color = astra.onBackground.copy(alpha = 0.72f),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Step ${snapshot.requiredGrantedCount.coerceAtLeast(1)} of ${snapshot.requiredTotal.coerceAtLeast(1)} required",
            style = MaterialTheme.typography.labelMedium,
            color = astra.primary,
        )
        Spacer(Modifier.height(12.dp))

        snapshot.statuses.forEach { status ->
            val descriptor = status.descriptor
            AstraCard(Modifier.padding(bottom = 12.dp)) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            descriptor.title,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        StatusPill(
                            text = if (status.state.isGranted) "Granted" else if (descriptor.optional) "Optional" else "Required",
                            positive = status.state.isGranted,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        descriptor.purpose,
                        style = MaterialTheme.typography.bodySmall,
                        color = astra.onBackground.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Used by: " + descriptor.dependentFeatures.take(3).joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = astra.primary.copy(alpha = 0.9f),
                    )
                    Spacer(Modifier.height(12.dp))
                    if (status.state.isGranted) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = astra.success, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Ready", style = MaterialTheme.typography.labelMedium, color = astra.success)
                        }
                    } else {
                        AstraSecondaryButton(
                            text = "Open ${descriptor.title} settings",
                            onClick = { viewModel.request(descriptor.id) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        AstraPrimaryButton(
            text = if (snapshot.overlayReady) "Finish setup" else "Continue without overlay",
            onClick = { viewModel.complete(onFinished) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "You can change any of this later in the Permission Center.",
            style = MaterialTheme.typography.labelSmall,
            color = astra.onBackground.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 10.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@HiltViewModel
class PermissionViewModel @Inject constructor(
    private val permissionManager: PermissionManager,
    private val preferencesStore: PreferencesStore,
) : ViewModel() {
    private val _snapshot = MutableStateFlow(permissionManager.current())
    val snapshot: StateFlow<PermissionSnapshot> = _snapshot.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun refresh() {
        _snapshot.value = permissionManager.refresh()
    }

    fun request(id: AccessId) {
        val launched = permissionManager.requestAccess(id)
        if (!launched) {
            _message.value = "Android did not open that settings screen. Open Settings manually and search for AstraTouch."
        }
    }

    fun dismissMessage() { _message.value = null }

    fun complete(onDone: () -> Unit) {
        viewModelScope.launch {
            preferencesStore.setOnboardingComplete(true)
            onDone()
        }
    }

    val context: android.content.Context? = null
}

/** Kept for the accessibility "open settings" button on permission screens. */
@Composable
fun AccessStateLabel(state: AccessState) {
    val positive = state.isGranted
    StatusPill(
        text = when (state) {
            AccessState.GRANTED -> "Granted"
            AccessState.DENIED -> "Not granted"
            AccessState.PERMANENTLY_DENIED -> "Blocked"
            AccessState.RESTRICTED -> "Restricted"
            AccessState.NOT_REQUIRED -> "Not needed"
        },
        positive = positive,
    )
}
