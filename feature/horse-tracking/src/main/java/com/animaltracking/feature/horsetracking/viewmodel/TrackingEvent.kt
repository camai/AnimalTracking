package com.animaltracking.feature.horsetracking.viewmodel

import com.animaltracking.domain.error.DomainError

sealed interface TrackingEvent {
    data class ShowError(val error: DomainError) : TrackingEvent
}
