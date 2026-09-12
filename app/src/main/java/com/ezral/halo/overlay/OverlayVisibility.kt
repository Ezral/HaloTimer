package com.ezral.halo.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Activity instances may briefly overlap when the menu is reopened. */
object OverlayVisibility {
    private val menus = mutableSetOf<Any>()
    private val visible = MutableStateFlow(false)
    val changes = visible.asStateFlow()
    val menuVisible get() = visible.value
    private val fullScreens = mutableSetOf<Any>()
    private val fullScreen = MutableStateFlow(false)
    val fullScreenChanges = fullScreen.asStateFlow()
    val fullScreenVisible get() = fullScreen.value
    fun fullScreenShown(owner: Any) { fullScreens += owner; fullScreen.value = true }
    fun fullScreenHidden(owner: Any) { fullScreens -= owner; fullScreen.value = fullScreens.isNotEmpty() }
    fun shown(menu: Any) { menus += menu; visible.value = true }
    fun hidden(menu: Any) { menus -= menu; visible.value = menus.isNotEmpty() }
}
