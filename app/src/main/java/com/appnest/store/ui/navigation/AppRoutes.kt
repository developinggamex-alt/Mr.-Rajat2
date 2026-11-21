package com.appnest.store.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute {
    @Serializable
    data object Home : AppRoute

    @Serializable
    data object Search : AppRoute

    @Serializable
    data object Downloads : AppRoute

    @Serializable
    data object Profile : AppRoute

    @Serializable
    data class AppDetail(val appId: String) : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data object Updates : AppRoute

    @Serializable
    data object Privacy : AppRoute

    @Serializable
    data object Help : AppRoute

    @Serializable
    data object Admin : AppRoute
}
