package com.syhung.androidaiautomation

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import org.json.JSONObject
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class GeminiReply(
    val text: String,
    val promptTokens: Int,
    val outputTokens: Int,
    val totalTokens: Int
)

object GeminiCloudClient {
    private const val MODEL = "gemini-3.8-flash"
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
    private const val PREFS = "gemini_resilience"
    private const val MAX_ATTEMPTS = 3
    private const val FAILURE_THRESHOLD = 5
    private const val OPEN_MS = 5 * 60 * 1000L
    private class HttpStatusException(val statusCode: Int, message: String) : IOException(message)

    @Synchronized
    fun ask(context: Context, apiKey: String, prompt: String): GeminiReply =
        askInternal(context, apiKey, prompt, null, false)

    @Synchronized
    fun analyzeImage(context: Context, apiKey: String, prompt: String, image: Bitmap): GeminiReply {
        require(prompt.isNotBlank() && prompt.length <= 4_000)
        val first = askInternal(context, apiKey, prompt, image, true)
        val valid = try {
            val json = JSONObject(first.text)
            json.has("summary") && json.has("visible_controls") &&
                json.has("possible_blocker") && json.has("suggested_next_step")
        } catch (_: Exception) { false }
        if (valid) return first
        val corrected = askInternal(
            context,
            apiKey,
            "Repair the following visual analysis into one valid JSON object with exactly these keys: summary, visible_controls, possible_blocker, suggested_next_step. Do not add markdown fences or any instructions to execute actions. Input: ${first.text.take(3_000)}",
            null,
            true
        )
        try {
            val json = JSONObject(corrected.text)
            require(json.has("summary") && json.has("visible_controls") &&
                json.has("possible_blocker") && json.has("suggested_next_step"))
        } catch (_: Exception) {
            throw IOException("Gemini returned an invalid visual-analysis schema.")
        }
        return corrected
    }

    private fun askInternal(context: Context, apiKey: String, prompt: String, image: Bitmap?, forceJson: Boolean): GeminiReply {
        require(prompt.isNotBlank() && prompt.length <= 4_000)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val openUntil = prefs.getLong("open_until", 0L)
        check(now >= openUntil) {
            "Cloud circuit breaker is open. Local-only mode remains available; retry after cooldown."
        }

        var lastError: Exception? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            try {
                val reply = request(apiKey, prompt, image, forceJson)
                val requestNow = System.currentTimeMillis()
                val oldTimes = try { JSONArray(prefs.getString("request_times", "[]")) } catch (_: Exception) { JSONArray() }
                val recentTimes = JSONArray()
                for (i in 0 until oldTimes.length()) {
                    val timestamp = oldTimes.optLong(i, 0L)
                    if (timestamp > requestNow - 60_000L) recentTimes.put(timestamp)
                }
                recentTimes.put(requestNow)
                prefs.edit()
                    .putInt("failures", 0)
                    .putLong("open_until", 0L)
                    .putLong("requests", prefs.getLong("requests", 0L) + 1L)
                    .putString("request_times", recentTimes.toString())
                    .putLong("input_tokens", prefs.getLong("input_tokens", 0L) + reply.promptTokens)
                    .putLong("output_tokens", prefs.getLong("output_tokens", 0L) + reply.outputTokens)
                    .putLong("total_tokens", prefs.getLong("total_tokens", 0L) + reply.totalTokens)
                    .apply()
                return reply
            } catch (error: Exception) {
                lastError = error
                val retryable = if (error is HttpStatusException) {
                    error.statusCode == 429 || error.statusCode in 500..599
                } else {
                    error is IOException
                }
                if (!retryable || attempt == MAX_ATTEMPTS - 1) break
                Thread.sleep(1_000L shl attempt)
            }
        }

        val failures = prefs.getInt("failures", 0) + 1
        val editor = prefs.edit().putInt("failures", failures)
        if (failures >= FAILURE_THRESHOLD) editor.putLong("open_until", System.currentTimeMillis() + OPEN_MS)
        editor.apply()
        throw lastError ?: IOException("Gemini request failed.")
    }

    fun usage(context: Context): String {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val times = try { JSONArray(p.getString("request_times", "[]")) } catch (_: Exception) { JSONArray() }
        val now = System.currentTimeMillis()
        var rpm = 0
        for (i in 0 until times.length()) if (times.optLong(i, 0L) > now - 60_000L) rpm++
        return "RPM: $rpm | Total requests: ${p.getLong("requests", 0L)} | Input tokens: ${p.getLong("input_tokens", 0L)} | Output tokens: ${p.getLong("output_tokens", 0L)} | Total tokens: ${p.getLong("total_tokens", 0L)}"
    }

    private fun request(apiKey: String, prompt: String, image: Bitmap?, forceJson: Boolean): GeminiReply {
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }

        try {
            val contentParts = org.json.JSONArray().put(JSONObject().put("text", prompt))
            if (image != null) {
                val scale = minOf(1.0, 1024.0 / maxOf(image.width, image.height).toDouble())
                val resized = if (scale < 1.0) Bitmap.createScaledBitmap(
                    image,
                    (image.width * scale).toInt().coerceAtLeast(1),
                    (image.height * scale).toInt().coerceAtLeast(1),
                    true
                ) else image
                val output = ByteArrayOutputStream()
                try {
                    check(resized.compress(Bitmap.CompressFormat.JPEG, 78, output))
                    val encoded = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
                    contentParts.put(JSONObject().put("inlineData",
                        JSONObject().put("mimeType", "image/jpeg").put("data", encoded)))
                } finally {
                    output.close()
                    if (resized !== image) resized.recycle()
                }
            }
            val generationConfig = JSONObject()
                .put("temperature", 0.1)
                .put("maxOutputTokens", if (image != null) 512 else 128)
            if (forceJson) generationConfig.put("responseMimeType", "application/json")
            val body = JSONObject()
                .put("contents", org.json.JSONArray().put(
                    JSONObject().put("parts", contentParts)
                ))
                .put("generationConfig", generationConfig)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText().take(100_000) }.orEmpty()
            if (code !in 200..299) {
                val message = try {
                    JSONObject(response).optJSONObject("error")?.optString("message")
                } catch (_: Exception) { null }
                throw HttpStatusException(code, "HTTP $code: ${message ?: "Gemini request rejected"}")
            }

            val root = JSONObject(response)
            val candidates = root.optJSONArray("candidates")
                ?: throw IOException("Gemini returned no candidates.")
            val candidate = candidates.optJSONObject(0)
                ?: throw IOException("Gemini returned an empty candidate.")
            val content = candidate.optJSONObject("content")
                ?: throw IOException("Gemini response content is missing.")
            val responseParts = content.optJSONArray("parts")
                ?: throw IOException("Gemini response parts are missing.")
            val answer = buildString {
                for (i in 0 until responseParts.length()) {
                    append(responseParts.optJSONObject(i)?.optString("text").orEmpty())
                }
            }.trim()
            if (answer.isEmpty()) throw IOException("Gemini returned an empty response.")
            val usage = root.optJSONObject("usageMetadata")
            return GeminiReply(
                answer,
                usage?.optInt("promptTokenCount", 0) ?: 0,
                usage?.optInt("candidatesTokenCount", 0) ?: 0,
                usage?.optInt("totalTokenCount", 0) ?: 0
            )
        } finally {
            connection.disconnect()
        }
    }
}
