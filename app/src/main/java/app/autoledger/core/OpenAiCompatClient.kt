package app.autoledger.core

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class OpenAiCompatClient(
  private val baseUrl: String,
  private val apiKey: String,
  private val model: String,
) {
  private val TAG = "AutoLedger/OpenAI"
  private val client = OkHttpClient.Builder()
    .callTimeout(60, TimeUnit.SECONDS)
    .build()

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  fun parseExpenseFromScreenshot(bitmap: Bitmap): ParsedExpense {
    Log.i(TAG, "parseExpenseFromScreenshot bitmap=${bitmap.width}x${bitmap.height} baseUrl=$baseUrl model=$model")
    val jpgBytes = ByteArrayOutputStream().use { os ->
      bitmap.compress(Bitmap.CompressFormat.JPEG, 80, os)
      os.toByteArray()
    }
    val b64 = Base64.encodeToString(jpgBytes, Base64.NO_WRAP)

    val req = JSONObject().apply {
      put("model", model)
      put("temperature", 0)
      put("messages", JSONArray().apply {
        put(JSONObject().apply {
          put("role", "system")
          put("content", "You are an accounting extractor. From a mobile payment success screenshot (Chinese UI), extract ONE expense record. Output ONLY valid JSON, no markdown.")
        })
        put(JSONObject().apply {
          put("role", "user")
          put("content", JSONArray().apply {
            put(JSONObject().apply {
              put("type", "text")
              put("text", "Extract JSON with keys: time_local, app (WeChat|Alipay|Bank|Unknown), amount (negative for expense), currency (CNY), merchant, note, confidence (0-1), raw.")
            })
            put(JSONObject().apply {
              put("type", "image_url")
              put("image_url", JSONObject().apply {
                put("url", "data:image/jpeg;base64,$b64")
              })
            })
          })
        })
      })
    }

    val body = req.toString().toRequestBody("application/json".toMediaType())
    val url = baseUrl.trimEnd('/') + "/v1/chat/completions"
    Log.i(TAG, "POST $url")

    val request = Request.Builder()
      .url(url)
      .addHeader("Authorization", "Bearer $apiKey")
      .addHeader("Content-Type", "application/json")
      // OpenRouter recommended headers (safe even for non-OpenRouter)
      .addHeader("HTTP-Referer", "https://auto-ledger.local")
      .addHeader("X-Title", "Auto Ledger")
      .post(body)
      .build()

    client.newCall(request).execute().use { resp ->
      val text = resp.body?.string() ?: ""
      Log.i(TAG, "response code=${resp.code} len=${text.length}")
      if (!resp.isSuccessful) {
        val snippet = if (text.length > 1200) text.substring(0, 1200) + "…" else text
        Log.w(TAG, "HTTP ${resp.code} body=$snippet")
        throw RuntimeException("HTTP ${resp.code}: $snippet")
      }
      val obj = JSONObject(text)
      val content = obj.getJSONArray("choices")
        .getJSONObject(0)
        .getJSONObject("message")
        .getString("content")
      Log.i(TAG, "model content len=${content.length}")
      return json.decodeFromString(ParsedExpense.serializer(), content)
    }
  }

  @Serializable
  data class ParsedExpense(
    val time_local: String? = null,
    val app: String? = null,
    val amount: Double? = null,
    val currency: String? = "CNY",
    val merchant: String? = null,
    val note: String? = null,
    val confidence: Double? = null,
    val raw: String? = null,
  )
}
