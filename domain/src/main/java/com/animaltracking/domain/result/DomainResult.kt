package com.animaltracking.domain.result

sealed interface DomainResult<out T, out E> {
    data class Success<out T>(val data: T) : DomainResult<T, Nothing>
    data class Failure<out E>(val error: E) : DomainResult<Nothing, E>
}