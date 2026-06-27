package com.example.network

import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class MessageDto(
    val role: String,
    val content: String
)

@JsonClass(generateAdapter = true)
data class ChatCompletionRequest(
    val model: String,
    val messages: List<MessageDto>,
    val temperature: Double = 0.8,
    val max_tokens: Int = 2048
)

@JsonClass(generateAdapter = true)
data class ChoiceDto(
    val index: Int,
    val message: MessageDto,
    val finish_reason: String?
)

@JsonClass(generateAdapter = true)
data class ChatCompletionResponse(
    val id: String?,
    val choices: List<ChoiceDto>?
)

interface OpenAiApi {
    @POST("v1/chat/completions")
    suspend fun getCompletion(
        @Header("Authorization") apiKeyHeader: String,
        @Body request: ChatCompletionRequest
    ): ChatCompletionResponse
}

object OpenAiClient {
    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val httpClient = OkHttpClient.Builder()
        .addInterceptor(logging)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val api: OpenAiApi = Retrofit.Builder()
        .baseUrl("https://api.openai.com/")
        .addConverterFactory(MoshiConverterFactory.create())
        .client(httpClient)
        .build()
        .create(OpenAiApi::class.java)
}
