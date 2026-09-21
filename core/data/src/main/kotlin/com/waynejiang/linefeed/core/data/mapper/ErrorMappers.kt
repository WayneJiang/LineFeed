package com.waynejiang.linefeed.core.data.mapper

import com.waynejiang.linefeed.core.domain.model.AppError
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException

/** Maps any `Throwable` raised while talking to a remote source into the small UI-facing [AppError] taxonomy. */
fun Throwable.toAppError(): AppError = when (this) {
    is UnknownHostException, is ConnectException -> AppError.OFFLINE
    is SocketTimeoutException -> AppError.TIMEOUT
    is HttpException -> AppError.SERVER
    is SerializationException -> AppError.PARSE
    else -> AppError.UNKNOWN
}
