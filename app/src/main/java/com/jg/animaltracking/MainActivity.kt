package com.jg.animaltracking

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.animaltracking.feature.tracking.TrackingNavRoute
import com.animaltracking.feature.tracking.trackingGraph
import com.jg.animaltracking.ui.theme.AnimalTrackingTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AnimalTrackingTheme {
                val navController = rememberNavController()
                NavHost(
                    navController = navController,
                    startDestination = TrackingNavRoute
                ) {
                    trackingGraph()
                }
            }
        }
    }
}