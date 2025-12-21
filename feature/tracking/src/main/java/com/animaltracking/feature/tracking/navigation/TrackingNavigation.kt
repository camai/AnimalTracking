package com.animaltracking.feature.tracking.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.animaltracking.feature.tracking.screen.TrackingRoute
import kotlinx.serialization.Serializable

@Serializable
object TrackingNavRoute

fun NavGraphBuilder.trackingGraph() {
    composable<TrackingNavRoute> {
        TrackingRoute()
    }
}