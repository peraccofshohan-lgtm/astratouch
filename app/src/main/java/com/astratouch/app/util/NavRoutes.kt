package com.astratouch.app.util

/**
 * Single definition of every navigation destination (Part 15: all 24 screens).
 * Routes are also used by the action engine so a floating action can deep-link
 * straight into an AstraTouch screen.
 */
object NavRoutes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val PERMISSION_SETUP = "permission_setup"
    const val HOME = "home"
    const val BUBBLE = "bubble"
    const val RADIAL = "radial"
    const val QUICK_CONTROLS = "quick_controls"
    const val GESTURES = "gestures"
    const val ACTIONS = "actions"
    const val MACROS = "macros"
    const val MACRO_EDITOR = "macro_editor"
    const val APPS = "apps"
    const val PROFILES = "profiles"
    const val THEMES = "themes"
    const val SCREENSHOTS = "screenshots"
    const val CLIPBOARD = "clipboard"
    const val NOTES = "notes"
    const val CALCULATOR = "calculator"
    const val TIMER = "timer"
    const val LAB = "lab"
    const val PRIVACY = "privacy"
    const val PERMISSIONS = "permissions"
    const val SETTINGS = "settings"
    const val ABOUT = "about"

    fun macroEditor(macroId: String): String = "$MACRO_EDITOR/$macroId"

    fun macroEditorPattern(): String = "$MACRO_EDITOR/{macroId}"

    /** All 24 destinations, used by the navigation test and the About screen. */
    val all: List<String> = listOf(
        SPLASH, ONBOARDING, PERMISSION_SETUP, HOME, BUBBLE, RADIAL, QUICK_CONTROLS, GESTURES,
        ACTIONS, MACROS, MACRO_EDITOR, APPS, PROFILES, THEMES, SCREENSHOTS, CLIPBOARD, NOTES,
        CALCULATOR, TIMER, LAB, PRIVACY, PERMISSIONS, SETTINGS, ABOUT,
    )
}
