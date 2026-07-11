package com.example.network

import com.squareup.moshi.JsonClass
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
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

    fun streamChatCompletions(
        apiKey: String,
        model: String,
        messages: List<MessageDto>,
        baseUrl: String = "https://api.openai.com/"
    ): Flow<String> = flow {
        val json = JSONObject()
        json.put("model", model)
        val messagesArray = JSONArray()
        for (msg in messages) {
            val msgObj = JSONObject()
            msgObj.put("role", msg.role)
            msgObj.put("content", msg.content)
            messagesArray.put(msgObj)
        }
        json.put("messages", messagesArray)
        json.put("temperature", 0.85)
        json.put("max_tokens", 2048)
        json.put("stream", true)

        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = json.toString().toRequestBody(mediaType)
        
        val cleanBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val fullUrl = if (cleanBaseUrl.contains("v1")) {
            if (cleanBaseUrl.endsWith("v1/")) "${cleanBaseUrl}chat/completions" else "${cleanBaseUrl}/chat/completions"
        } else {
            "${cleanBaseUrl}v1/chat/completions"
        }

        val request = Request.Builder()
            .url(fullUrl)
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        val streamClient = httpClient.newBuilder()
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            .build()

        streamClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorMsg = response.body?.string() ?: ""
                throw Exception("OpenAI API streaming error: Code ${response.code}, Message: $errorMsg")
            }
            val source = response.body?.source() ?: throw Exception("Response body source is null")
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (line.startsWith("data: ")) {
                    val data = line.substring(6).trim()
                    if (data == "[DONE]") {
                        break
                    }
                    try {
                        val chunkJson = JSONObject(data)
                        val choices = chunkJson.optJSONArray("choices")
                        if (choices != null && choices.length() > 0) {
                            val choice = choices.getJSONObject(0)
                            val delta = choice.optJSONObject("delta")
                            val content = delta?.optString("content") ?: ""
                            if (content.isNotEmpty()) {
                                emit(content)
                            }
                        }
                    } catch (e: Exception) {
                        // Ignore standard deserialization or blank-line anomalies
                    }
                }
            }
        }
    }
}
