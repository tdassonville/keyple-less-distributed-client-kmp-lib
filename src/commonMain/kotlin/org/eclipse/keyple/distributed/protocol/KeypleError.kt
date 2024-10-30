package org.eclipse.keyple.distributed.protocol

sealed class KeypleResult<T> {
    data class Success<T>(val data: T) : KeypleResult<T>()
    data class Failure<T>(val error: KeypleError) : KeypleResult<T>()
}

data class KeypleError(val statusCode: Int, val message: String)