package com.ezral.halo.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Activity instances may briefly overlap when the menu is reopened. */
object OverlayVisibility {
    private val menus = mutableSetOf<Any>()
    private val visible = MutableStateFlow(false)
    val changes = visible.asStateFlow()
    val menuVisible get() = visible.value
    fun shown(menu: Any) { menus += menu; visible.value = true }
    fun hidden(menu: Any) { menus -= menu; visible.value = menus.isNotEmpty() }
}
