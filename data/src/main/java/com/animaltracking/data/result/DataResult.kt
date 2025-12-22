package com.animaltracking.data.result

import com.animaltracking.data.error.DataError

sealed interface DataResult<out T> {
    data class Success<out T>(val data: T) : DataResult<T>
    data class Fail(val error: DataError) : DataResult<Nothing>
}
