package com.astratouch.app.data.mapper

import com.astratouch.app.data.local.entity.BubbleConfigEntity
import com.astratouch.app.data.local.entity.ClipboardRecordEntity
import com.astratouch.app.data.local.entity.FavoriteAppEntity
import com.astratouch.app.data.local.entity.GestureMappingEntity
import com.astratouch.app.data.local.entity.MacroEntity
import com.astratouch.app.data.local.entity.MacroExecutionEntity
import com.astratouch.app.data.local.entity.MacroStepEntity
import com.astratouch.app.data.local.entity.NoteEntity
import com.astratouch.app.data.local.entity.ProfileEntity
import com.astratouch.app.data.local.entity.RadialConfigEntity
import com.astratouch.app.data.local.entity.RadialItemEntity
import com.astratouch.app.data.local.entity.ThemeConfigEntity
import com.astratouch.app.data.local.entity.TimerEntity
import com.astratouch.app.domain.model.AnimationStyle
import com.astratouch.app.domain.model.AstraTimer
import com.astratouch.app.domain.model.BubbleConfig
import com.astratouch.app.domain.model.BubbleShape
import com.astratouch.app.domain.model.ClipboardRecord
import com.astratouch.app.domain.model.DockSide
import com.astratouch.app.domain.model.FavoriteApp
import com.astratouch.app.domain.model.GestureMapping
import com.astratouch.app.domain.model.GestureType
import com.astratouch.app.domain.model.Macro
import com.astratouch.app.domain.model.MacroExecutionRecord
import com.astratouch.app.domain.model.MacroRunStatus
import com.astratouch.app.domain.model.MacroStep
import com.astratouch.app.domain.model.Note
import com.astratouch.app.domain.model.Profile
import com.astratouch.app.domain.model.ProfileType
import com.astratouch.app.domain.model.RadialConfig
import com.astratouch.app.domain.model.RadialLayout
import com.astratouch.app.domain.model.RadialMenuItem
import com.astratouch.app.domain.model.SnapMode
import com.astratouch.app.domain.model.StepType
import com.astratouch.app.domain.model.ThemeConfig

fun ProfileEntity.toDomain(): Profile = Profile(
    id = id,
    type = ProfileType.fromId(type),
    name = name,
    description = description,
    themeId = themeId,
    bubbleConfigId = bubbleConfigId,
    radialConfigId = radialConfigId,
    isActive = isActive,
    autoSwitchPackage = autoSwitchPackage,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sortOrder = sortOrder,
)

fun Profile.toEntity(): ProfileEntity = ProfileEntity(
    id = id,
    type = type.id,
    name = name,
    description = description,
    themeId = themeId,
    bubbleConfigId = bubbleConfigId,
    radialConfigId = radialConfigId,
    isActive = isActive,
    autoSwitchPackage = autoSwitchPackage,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sortOrder = sortOrder,
)

fun BubbleConfigEntity.toDomain(): BubbleConfig = BubbleConfig(
    id = id,
    profileId = profileId,
    sizeDp = sizeDp,
    shape = BubbleShape.fromName(shape),
    iconKey = iconKey,
    opacity = opacity,
    colorStart = colorStart,
    colorEnd = colorEnd,
    borderWidthDp = borderWidthDp,
    shadowRadiusDp = shadowRadiusDp,
    blurRadiusDp = blurRadiusDp,
    iconTint = iconTint,
    iconSizeDp = iconSizeDp,
    edgeMarginDp = edgeMarginDp,
    snapMode = SnapMode.fromName(snapMode),
    dockSide = DockSide.fromName(dockSide),
    positionX = positionX,
    positionY = positionY,
    autoHideMs = autoHideMs,
    autoDimAlpha = autoDimAlpha,
    hapticsEnabled = hapticsEnabled,
    animationStyle = AnimationStyle.fromName(animationStyle),
    animationSpeed = animationSpeed,
    startupOnBoot = startupOnBoot,
    showEdgeHandle = showEdgeHandle,
    edgePanelEnabled = edgePanelEnabled,
)

fun BubbleConfig.toEntity(): BubbleConfigEntity = BubbleConfigEntity(
    id = id,
    profileId = profileId,
    sizeDp = sizeDp,
    shape = shape.name,
    iconKey = iconKey,
    opacity = opacity,
    colorStart = colorStart,
    colorEnd = colorEnd,
    borderWidthDp = borderWidthDp,
    shadowRadiusDp = shadowRadiusDp,
    blurRadiusDp = blurRadiusDp,
    iconTint = iconTint,
    iconSizeDp = iconSizeDp,
    edgeMarginDp = edgeMarginDp,
    snapMode = snapMode.name,
    dockSide = dockSide.name,
    positionX = positionX,
    positionY = positionY,
    autoHideMs = autoHideMs,
    autoDimAlpha = autoDimAlpha,
    hapticsEnabled = hapticsEnabled,
    animationStyle = animationStyle.name,
    animationSpeed = animationSpeed,
    startupOnBoot = startupOnBoot,
    showEdgeHandle = showEdgeHandle,
    edgePanelEnabled = edgePanelEnabled,
)

fun RadialConfigEntity.toDomain(): RadialConfig = RadialConfig(
    id = id,
    profileId = profileId,
    layout = RadialLayout.fromName(layout),
    radiusDp = radiusDp,
    spacingDp = spacingDp,
    itemSizeDp = itemSizeDp,
    backgroundStart = backgroundStart,
    backgroundEnd = backgroundEnd,
    backgroundOpacity = backgroundOpacity,
    blurRadiusDp = blurRadiusDp,
    accent = accent,
    animationStyle = AnimationStyle.fromName(animationStyle),
    animationSpeed = animationSpeed,
    hapticsEnabled = hapticsEnabled,
    showLabels = showLabels,
)

fun RadialConfig.toEntity(): RadialConfigEntity = RadialConfigEntity(
    id = id,
    profileId = profileId,
    layout = layout.name,
    radiusDp = radiusDp,
    spacingDp = spacingDp,
    itemSizeDp = itemSizeDp,
    backgroundStart = backgroundStart,
    backgroundEnd = backgroundEnd,
    backgroundOpacity = backgroundOpacity,
    blurRadiusDp = blurRadiusDp,
    accent = accent,
    animationStyle = animationStyle.name,
    animationSpeed = animationSpeed,
    hapticsEnabled = hapticsEnabled,
    showLabels = showLabels,
)

fun RadialItemEntity.toDomain(): RadialMenuItem = RadialMenuItem(
    id = id,
    radialConfigId = radialConfigId,
    page = page,
    slot = slot,
    actionId = actionId,
    label = label,
    iconKey = iconKey,
    category = category,
    isFavorite = isFavorite,
)

fun RadialMenuItem.toEntity(): RadialItemEntity = RadialItemEntity(
    id = id,
    radialConfigId = radialConfigId,
    page = page,
    slot = slot,
    actionId = actionId,
    label = label,
    iconKey = iconKey,
    category = category,
    isFavorite = isFavorite,
)

fun GestureMappingEntity.toDomain(): GestureMapping? {
    val gesture = GestureType.fromName(gesture) ?: return null
    return GestureMapping(
        id = id,
        profileId = profileId,
        gesture = gesture,
        actionId = actionId,
        swipeThresholdPx = swipeThresholdPx,
        sensitivity = sensitivity,
        hapticsEnabled = hapticsEnabled,
    )
}

fun GestureMapping.toEntity(): GestureMappingEntity = GestureMappingEntity(
    id = id,
    profileId = profileId,
    gesture = gesture.name,
    actionId = actionId,
    swipeThresholdPx = swipeThresholdPx,
    sensitivity = sensitivity,
    hapticsEnabled = hapticsEnabled,
)

fun MacroEntity.toDomain(): Macro = Macro(
    id = id,
    profileId = profileId,
    name = name,
    description = description,
    iconKey = iconKey,
    isEnabled = isEnabled,
    timeoutMs = timeoutMs,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sortOrder = sortOrder,
    lastRunAt = lastRunAt,
    lastRunStatus = MacroRunStatus.fromName(lastRunStatus),
)

fun Macro.toEntity(): MacroEntity = MacroEntity(
    id = id,
    profileId = profileId,
    name = name,
    description = description,
    iconKey = iconKey,
    isEnabled = isEnabled,
    timeoutMs = timeoutMs,
    createdAt = createdAt,
    updatedAt = updatedAt,
    sortOrder = sortOrder,
    lastRunAt = lastRunAt,
    lastRunStatus = lastRunStatus.name,
)

fun MacroStepEntity.toDomain(): MacroStep = MacroStep(
    id = id,
    macroId = macroId,
    order = order,
    type = StepType.fromName(type),
    actionId = actionId,
    payload = payload,
    delayMs = delayMs,
    label = label,
)

fun MacroStep.toEntity(): MacroStepEntity = MacroStepEntity(
    id = id,
    macroId = macroId,
    order = order,
    type = type.name,
    actionId = actionId,
    payload = payload,
    delayMs = delayMs,
    label = label,
)

fun MacroExecutionEntity.toDomain(): MacroExecutionRecord = MacroExecutionRecord(
    id = id,
    macroId = macroId,
    macroName = macroName,
    startedAt = startedAt,
    finishedAt = finishedAt,
    status = MacroRunStatus.fromName(status),
    detail = detail,
)

fun ThemeConfigEntity.toDomain(): ThemeConfig = ThemeConfig(
    id = id,
    presetId = presetId,
    name = name,
    background = background,
    surface = surface,
    primary = primary,
    secondary = secondary,
    tertiary = tertiary,
    onBackground = onBackground,
    isDark = isDark,
    cornerRadiusDp = cornerRadiusDp,
    typographyScale = typographyScale,
    animationSpeed = animationSpeed,
    hapticIntensity = hapticIntensity,
    useMonospace = useMonospace,
)

fun ThemeConfig.toEntity(): ThemeConfigEntity = ThemeConfigEntity(
    id = id,
    presetId = presetId,
    name = name,
    background = background,
    surface = surface,
    primary = primary,
    secondary = secondary,
    tertiary = tertiary,
    onBackground = onBackground,
    isDark = isDark,
    cornerRadiusDp = cornerRadiusDp,
    typographyScale = typographyScale,
    animationSpeed = animationSpeed,
    hapticIntensity = hapticIntensity,
    useMonospace = useMonospace,
)

fun FavoriteAppEntity.toDomain(): FavoriteApp = FavoriteApp(
    packageName = packageName,
    label = label,
    isFavorite = isFavorite,
    group = groupName,
    lastUsedAt = lastUsedAt,
    launchCount = launchCount,
)

fun FavoriteApp.toEntity(): FavoriteAppEntity = FavoriteAppEntity(
    packageName = packageName,
    label = label,
    isFavorite = isFavorite,
    category = group.ifBlank { "uncategorized" },
    groupName = group,
    lastUsedAt = lastUsedAt,
    launchCount = launchCount,
)

fun NoteEntity.toDomain(): Note = Note(
    id = id,
    title = title,
    body = body,
    isPinned = isPinned,
    colorArgb = colorArgb,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Note.toEntity(): NoteEntity = NoteEntity(
    id = id,
    title = title,
    body = body,
    isPinned = isPinned,
    colorArgb = colorArgb,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ClipboardRecordEntity.toDomain(): ClipboardRecord = ClipboardRecord(
    id = id,
    content = content,
    isPinned = isPinned,
    createdAt = createdAt,
    sourcePackage = sourcePackage,
    charCount = charCount,
    isSensitiveRedacted = isSensitiveRedacted,
)

fun ClipboardRecord.toEntity(): ClipboardRecordEntity = ClipboardRecordEntity(
    id = id,
    content = content,
    isPinned = isPinned,
    createdAt = createdAt,
    sourcePackage = sourcePackage,
    charCount = charCount,
    isSensitiveRedacted = isSensitiveRedacted,
)

fun TimerEntity.toDomain(): AstraTimer = AstraTimer(
    id = id,
    label = label,
    durationMs = durationMs,
    remainingMs = remainingMs,
    isRunning = isRunning,
    endsAtEpochMs = endsAtEpochMs,
    ringtoneRequested = ringtoneRequested,
)

fun AstraTimer.toEntity(): TimerEntity = TimerEntity(
    id = id,
    label = label,
    durationMs = durationMs,
    remainingMs = remainingMs,
    isRunning = isRunning,
    endsAtEpochMs = endsAtEpochMs,
    ringtoneRequested = ringtoneRequested,
)
