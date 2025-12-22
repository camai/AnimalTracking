package com.animaltracking.data.error

sealed interface DataError {
    object DetectorUnavailable : DataError
    data class Unexpected(val throwable: Throwable) : DataError
}
