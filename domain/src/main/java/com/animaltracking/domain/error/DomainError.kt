package com.animaltracking.domain.error

sealed interface DomainError {
    object DetectorUnavailable : DomainError
    data class Unexpected(val throwable: Throwable) : DomainError
}