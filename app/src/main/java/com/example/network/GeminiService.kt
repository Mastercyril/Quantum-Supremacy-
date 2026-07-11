package com.example.network

import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val BASE_URL = "https://generativelanguage.googleapis.com"

    private fun getApiKey(): String {
        return BuildConfig.GEMINI_API_KEY.trim()
    }

    /**
     * General function to execute a Gemini generateContent request.
     */
    private suspend fun executeGenerateContent(
        model: String,
        payload: JSONObject
    ): String {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "Error: Gemini API Key is missing. Please add it to your environment secrets."
        }

        val url = "$BASE_URL/v1beta/models/$model:generateContent?key=$apiKey"
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val requestBody = payload.toString().toRequestBody(mediaType)

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e("GeminiService", "API call failed with code ${response.code}: $bodyString")
                return "API Error (${response.code}): $bodyString"
            }

            val json = JSONObject(bodyString)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                val firstCandidate = candidates.getJSONObject(0)
                val contentObj = firstCandidate.optJSONObject("content")
                val parts = contentObj?.optJSONArray("parts")
                if (parts != null && parts.length() > 0) {
                    val firstPart = parts.getJSONObject(0)
                    // Check if it's an image generation or text
                    if (firstPart.has("inlineData")) {
                        val inlineData = firstPart.getJSONObject("inlineData")
                        return inlineData.optString("data")
                    }
                    return firstPart.optString("text", "...")
                }
            }
            "No content returned from Gemini."
        } catch (e: Exception) {
            Log.e("GeminiService", "Exception executing generation", e)
            "Transmit Error: ${e.localizedMessage}"
        }
    }

    /**
     * 1. High Thinking Mode (gemini-3.1-pro-preview)
     * Set thinkingLevel to HIGH and do not set maxOutputTokens
     */
    suspend fun generateWithThinking(prompt: String, systemInstruction: String): String {
        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", prompt)
                }))
            }))
            put("generationConfig", JSONObject().apply {
                put("thinkingConfig", JSONObject().apply {
                    put("thinkingLevel", "HIGH")
                })
            })
            if (systemInstruction.isNotEmpty()) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", systemInstruction)
                    }))
                })
            }
        }
        return executeGenerateContent("gemini-3.1-pro-preview", payload)
    }

    /**
     * 2. Transcribe Audio (gemini-3.5-flash)
     */
    suspend fun transcribeAudio(audioBase64: String, mimeType: String): String {
        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", mimeType)
                            put("data", audioBase64)
                        })
                    })
                    put(JSONObject().apply {
                        put("text", "Transcribe this audio precisely. Return ONLY the transcription with no commentary.")
                    })
                })
            }))
        }
        return executeGenerateContent("gemini-3.5-flash", payload)
    }

    /**
     * 3. Analyze Video Content (gemini-3.1-pro-preview)
     */
    suspend fun analyzeVideo(videoBase64: String, mimeType: String, prompt: String): String {
        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", mimeType)
                            put("data", videoBase64)
                        })
                    })
                    put(JSONObject().apply {
                        put("text", prompt.ifEmpty { "Analyze this video for key actions and timeline information." })
                    })
                })
            }))
        }
        return executeGenerateContent("gemini-3.1-pro-preview", payload)
    }

    /**
     * 4. Analyze Image (gemini-3.1-pro-preview)
     */
    suspend fun analyzeImage(imageBase64: String, mimeType: String, prompt: String): String {
        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("inlineData", JSONObject().apply {
                            put("mimeType", mimeType)
                            put("data", imageBase64)
                        })
                    })
                    put(JSONObject().apply {
                        put("text", prompt.ifEmpty { "Analyze this image and explain in detail what you see." })
                    })
                })
            }))
        }
        return executeGenerateContent("gemini-3.1-pro-preview", payload)
    }

    /**
     * 5. Generate High Quality Images (gemini-3-pro-image-preview)
     * Affordances: (1K, 2K, 4K)
     */
    suspend fun generateHighQualityImage(prompt: String, sizeKey: String): String {
        // Size mapping for gemini specification
        val sizeString = when (sizeKey) {
            "1K" -> "1024x1024"
            "2K" -> "2048x2048"
            "4K" -> "4096x4096"
            else -> "1024x1024"
        }

        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", prompt)
                }))
            }))
            put("generationConfig", JSONObject().apply {
                put("imageConfig", JSONObject().apply {
                    put("aspectRatio", "1:1")
                    put("imageSize", sizeString)
                })
                put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
            })
        }
        return executeGenerateContent("gemini-3-pro-image-preview", payload)
    }

    /**
     * 5b. General Content Generation (gemini-3.5-flash)
     */
    suspend fun generateContent(prompt: String, systemInstruction: String = ""): String {
        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", prompt)
                }))
            }))
            if (systemInstruction.isNotEmpty()) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", systemInstruction)
                    }))
                })
            }
        }
        return executeGenerateContent("gemini-3.5-flash", payload)
    }

    /**
     * 6. Google Search Grounding (gemini-3.5-flash)
     */
    suspend fun generateWithSearch(prompt: String, systemInstruction: String): String {
        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", prompt)
                }))
            }))
            put("tools", JSONArray().put(JSONObject().apply {
                put("googleSearch", JSONObject())
            }))
            if (systemInstruction.isNotEmpty()) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", systemInstruction)
                    }))
                })
            }
        }
        return executeGenerateContent("gemini-3.5-flash", payload)
    }

    /**
     * 7. Google Maps Grounding (gemini-3.5-flash)
     */
    suspend fun generateWithMaps(prompt: String, systemInstruction: String): String {
        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", prompt)
                }))
            }))
            put("tools", JSONArray().put(JSONObject().apply {
                put("googleMaps", JSONObject())
            }))
            if (systemInstruction.isNotEmpty()) {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().apply {
                        put("text", systemInstruction)
                    }))
                })
            }
        }
        return executeGenerateContent("gemini-3.5-flash", payload)
    }

    /**
     * 8. Create and Edit Images (gemini-3.1-flash-image-preview)
     */
    suspend fun createOrEditImage(prompt: String, baseImageBase64: String? = null, mimeType: String? = null): String {
        val payload = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().apply {
                    if (baseImageBase64 != null && mimeType != null) {
                        put(JSONObject().apply {
                            put("inlineData", JSONObject().apply {
                                put("mimeType", mimeType)
                                put("data", baseImageBase64)
                            })
                        })
                    }
                    put(JSONObject().apply {
                        put("text", prompt)
                    })
                })
            }))
            put("generationConfig", JSONObject().apply {
                put("imageConfig", JSONObject().apply {
                    put("aspectRatio", "1:1")
                })
                put("responseModalities", JSONArray().put("TEXT").put("IMAGE"))
            })
        }
        return executeGenerateContent("gemini-3.1-flash-image-preview", payload)
    }

    /**
     * 9. Animate image into video using Veo (veo-3.1-fast-generate-preview)
     * Resolution: 1080p, Aspect ratio: 16:9 or 9:16
     */
    suspend fun animateWithVeo(prompt: String, baseImageBase64: String? = null, mimeType: String? = null, aspectRatio: String): String {
        val apiKey = getApiKey()
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "Error: Gemini API Key is missing."
        }

        val url = "$BASE_URL/v1beta/models/veo-3.1-fast-generate-preview:generateVideos?key=$apiKey"
        val mediaType = "application/json; charset=utf-8".toMediaType()

        val payload = JSONObject().apply {
            put("prompt", prompt)
            put("config", JSONObject().apply {
                put("numberOfVideos", 1)
                put("resolution", "1080p")
                put("aspectRatio", if (aspectRatio == "landscape") "16:9" else "9:16")
            })
        }

        val requestBody = payload.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val bodyString = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e("GeminiService", "Veo API call failed with code ${response.code}: $bodyString")
                return "Veo Error (${response.code}): $bodyString"
            }

            val json = JSONObject(bodyString)
            // Veo typically returns an asynchronous operation object
            val operationName = json.optString("name", "")
            if (operationName.isNotEmpty()) {
                "Veo Video Generation Operation started: $operationName\n(Note: Video operations run asynchronously in the cloud. Check back in the telemetry stream.)"
            } else {
                bodyString
            }
        } catch (e: Exception) {
            Log.e("GeminiService", "Exception executing Veo video generation", e)
            "Veo Transmit Error: ${e.localizedMessage}"
        }
    }
}
