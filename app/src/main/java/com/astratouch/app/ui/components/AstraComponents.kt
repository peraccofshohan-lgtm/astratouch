package com.astratouch.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.astratouch.app.ui.theme.LocalAstraColors
import com.astratouch.app.ui.theme.toColor

/** Screen scaffold with a consistent AstraTouch top bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AstraScaffold(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    val astra = LocalAstraColors.current
    Scaffold(
        containerColor = astra.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = astra.onBackground.copy(alpha = 0.6f),
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack, modifier = Modifier.semantics { contentDescription = "Back" }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = astra.onBackground)
                        }
                    }
                },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = astra.onBackground,
                ),
            )
        },
    ) { padding ->
        content(Modifier.padding(padding))
    }
}

/** Elevated card used everywhere in the app for grouping. */
@Composable
fun AstraCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val astra = LocalAstraColors.current
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) 0.985f else 1f, tween(90), label = "cardScale")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        astra.surface.copy(alpha = 0.98f),
                        astra.surface.copy(alpha = 0.86f),
                    ),
                ),
            )
            .border(1.dp, astra.outline.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable {
                        pressed = true
                        onClick()
                        pressed = false
                    }
                } else {
                    Modifier
                },
            )
            .padding(16.dp),
    ) { content() }
}

/** Section heading with optional caption. */
@Composable
fun SectionHeader(title: String, caption: String? = null, modifier: Modifier = Modifier) {
    val astra = LocalAstraColors.current
    Column(modifier = modifier.padding(vertical = 8.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = astra.primary,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified,
        )
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodySmall, color = astra.onBackground.copy(alpha = 0.6f))
        }
    }
}

/** Labelled switch row with a description and worked state feedback. */
@Composable
fun SettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val astra = LocalAstraColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (enabled) astra.primary else astra.onBackground.copy(alpha = 0.3f))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) astra.onBackground else astra.onBackground.copy(alpha = 0.45f),
            )
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = astra.onBackground.copy(alpha = if (enabled) 0.62f else 0.3f),
            )
        }
        Spacer(Modifier.width(12.dp))
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

/** Primary and secondary action buttons. */
@Composable
fun AstraPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun AstraSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, LocalAstraColors.current.outline),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text)
    }
}

/** Full-screen empty state with a real explanation, never a blank screen. */
@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val astra = LocalAstraColors.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.Inbox,
            contentDescription = null,
            tint = astra.primary.copy(alpha = 0.7f),
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = astra.onBackground.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            AstraPrimaryButton(actionLabel, onAction)
        }
    }
}

@Composable
fun LoadingState(message: String = "Loading…") {
    val astra = LocalAstraColors.current
    Column(
        Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = astra.primary)
        Spacer(Modifier.height(16.dp))
        Text(message, color = astra.onBackground.copy(alpha = 0.7f))
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)? = null) {
    val astra = LocalAstraColors.current
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = astra.danger, modifier = Modifier.size(44.dp))
        Spacer(Modifier.height(12.dp))
        Text("Something went wrong", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = astra.onBackground.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            AstraPrimaryButton("Retry", onRetry)
        }
    }
}

/** Permission-denied explainer: what is missing, why, and what still works. */
@Composable
fun PermissionDeniedState(
    permissionName: String,
    why: String,
    fallback: String,
    onGrant: () -> Unit,
) {
    val astra = LocalAstraColors.current
    Column(Modifier.padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(astra.warning.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = astra.warning, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(permissionName, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(12.dp))
        Text(why, style = MaterialTheme.typography.bodyMedium, color = astra.onBackground.copy(alpha = 0.72f))
        Spacer(Modifier.height(8.dp))
        Text(
            "Without it: $fallback",
            style = MaterialTheme.typography.bodySmall,
            color = astra.onBackground.copy(alpha = 0.6f),
        )
        Spacer(Modifier.height(16.dp))
        AstraPrimaryButton("Open permission settings", onGrant)
    }
}

/** Small pill showing a status, e.g. "Granted", "Running". */
@Composable
fun StatusPill(text: String, positive: Boolean, modifier: Modifier = Modifier) {
    val astra = LocalAstraColors.current
    val color = if (positive) astra.success else astra.warning
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f),
        contentColor = color,
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (positive) Icons.Filled.Check else Icons.Filled.ErrorOutline,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
        }
    }
}

/** Colour chip preview used by theme and bubble editors. */
@Composable
fun ColorDot(color: Long, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    Box(
        Modifier
            .size(if (selected) 34.dp else 28.dp)
            .clip(CircleShape)
            .background(Color(color.toInt()))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) LocalAstraColors.current.onBackground else Color.White.copy(alpha = 0.3f),
                shape = CircleShape,
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
    )
}
