package com.waynejiang.linefeed.core.data.mapper

import com.waynejiang.linefeed.core.domain.model.AppError
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.serialization.SerializationException
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ErrorMappersTest {
    @Test
    fun `unknown host and connect exceptions map to OFFLINE`() {
        assertEquals(AppError.OFFLINE, UnknownHostException().toAppError())
        assertEquals(AppError.OFFLINE, ConnectException().toAppError())
    }

    @Test
    fun `socket timeout maps to TIMEOUT`() {
        assertEquals(AppError.TIMEOUT, SocketTimeoutException().toAppError())
    }

    @Test
    fun `http exception maps to SERVER`() {
        val response = Response.error<Unit>(500, "".toResponseBody("application/json".toMediaType()))
        assertEquals(AppError.SERVER, HttpException(response).toAppError())
    }

    @Test
    fun `serialization exception maps to PARSE`() {
        assertEquals(AppError.PARSE, SerializationException("bad json").toAppError())
    }

    @Test
    fun `anything else maps to UNKNOWN`() {
        assertEquals(AppError.UNKNOWN, IllegalStateException("boom").toAppError())
    }
}
