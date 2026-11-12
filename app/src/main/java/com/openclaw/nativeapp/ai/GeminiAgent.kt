package com.openclaw.nativeapp.ai

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.openclaw.nativeapp.data.AppPreferences
import com.openclaw.nativeapp.data.LogManager
import com.openclaw.nativeapp.shizuku.DeviceController
import com.openclaw.nativeapp.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class GeminiAgent(private val context: Context) {
    private val TAG = "GeminiAgent"
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val preferences = AppPreferences.getInstance(context)

    suspend fun executeGoal(
        goal: String,
        onProgress: suspend (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val apiKey = preferences.geminiApiKey
        if (apiKey.isBlank()) {
            val err = "Error: Gemini API Key is missing. Please set it in Settings."
            LogManager.e(TAG, err)
            return@withContext err
        }

        val model = preferences.geminiModel.ifBlank { "gemini-1.5-flash" }
        LogManager.ai(TAG, "Starting goal execution: '$goal' using model: $model")
        onProgress("🧠 Analyzing goal: \"$goal\"")

        val conversationHistory = JsonArray()
        var stepCount = 0
        val maxSteps = 12

        while (stepCount < maxSteps) {
            stepCount++
            LogManager.i(TAG, "Step $stepCount/$maxSteps")

            // 1. Capture phone state: screen shot & UI text
            val screenshotBase64 = DeviceController.takeScreenshotBase64(context)
            val battery = DeviceController.getBatteryInfo()
            val fgApp = DeviceController.getForegroundApp()

            // 2. Prepare user prompt / step context
            val userContent = JsonObject().apply {
                addProperty("role", "user")
                val parts = JsonArray()

                val promptBuilder = StringBuilder()
                if (stepCount == 1) {
                    promptBuilder.append("USER GOAL: $goal\n\n")
                } else {
                    promptBuilder.append("Previous action performed. Current screen state is attached.\n")
                }
                promptBuilder.append("CURRENT STATUS: $battery | Foreground: $fgApp\n")
                promptBuilder.append("What is your next action to achieve the goal? If completed, call 'finish' or answer in plain text.")

                val textPart = JsonObject().apply { addProperty("text", promptBuilder.toString()) }
                parts.add(textPart)

                if (screenshotBase64 != null) {
                    val imagePart = JsonObject().apply {
                        val inlineData = JsonObject().apply {
                            addProperty("mime_type", "image/jpeg")
                            addProperty("data", screenshotBase64)
                        }
                        add("inline_data", inlineData)
                    }
                    parts.add(imagePart)
                }

                add("parts", parts)
            }
            conversationHistory.add(userContent)

            // 3. Build API Request Body with System Instruction and Function Calling
            val requestBodyJson = buildRequestBody(conversationHistory)
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val responseString = try {
                httpClient.newCall(request).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    if (!resp.isSuccessful) {
                        LogManager.e(TAG, "Gemini API error ${resp.code}: $body")
                        return@withContext "Gemini API Error (${resp.code}): $body"
                    }
                    body
                }
            } catch (e: Exception) {
                LogManager.e(TAG, "Network request to Gemini failed", e)
                return@withContext "Network request to Gemini failed: ${e.message}"
            }

            // 4. Parse Response
            val (finishText, functionCall) = parseGeminiResponse(responseString)

            if (functionCall != null) {
                val fnName = functionCall.get("name").asString
                val args = functionCall.getAsJsonObject("args")
                LogManager.ai(TAG, "Gemini called tool: $fnName with args: $args")

                if (fnName == "finish") {
                    val summary = args.get("summary")?.asString ?: "Goal completed."
                    onProgress("✅ $summary")
                    return@withContext summary
                }

                val actionResult = executeTool(fnName, args)
                onProgress("⚡ Step $stepCount: $actionResult")

                // Add model response and tool response to conversation history
                val modelContent = JsonObject().apply {
                    addProperty("role", "model")
                    val parts = JsonArray()
                    parts.add(JsonObject().apply { add("functionCall", functionCall) })
                    add("parts", parts)
                }
                conversationHistory.add(modelContent)

                val toolResponseContent = JsonObject().apply {
                    addProperty("role", "user")
                    val parts = JsonArray()
                    val fnResponse = JsonObject().apply {
                        addProperty("name", fnName)
                        val responseObj = JsonObject().apply { addProperty("result", actionResult) }
                        add("response", responseObj)
                    }
                    parts.add(JsonObject().apply { add("functionResponse", fnResponse) })
                    add("parts", parts)
                }
                conversationHistory.add(toolResponseContent)

                // Wait 1.5 seconds for UI transition before next step
                delay(1500)
            } else if (!finishText.isNullOrBlank()) {
                LogManager.ai(TAG, "Gemini final reply: $finishText")
                onProgress("💬 $finishText")
                return@withContext finishText
            } else {
                LogManager.w(TAG, "No text or tool call returned from Gemini.")
                return@withContext "No response action received from Gemini."
            }
        }

        val timeoutMsg = "Reached maximum steps ($maxSteps) for goal: $goal"
        LogManager.w(TAG, timeoutMsg)
        return@withContext timeoutMsg
    }

    private suspend fun executeTool(name: String, args: JsonObject): String {
        return try {
            when (name) {
                "tap" -> {
                    val x = args.get("x").asInt
                    val y = args.get("y").asInt
                    val ok = DeviceController.tap(x, y)
                    "Tapped at ($x, $y) -> success: $ok"
                }
                "swipe" -> {
                    val x1 = args.get("x1").asInt
                    val y1 = args.get("y1").asInt
                    val x2 = args.get("x2").asInt
                    val y2 = args.get("y2").asInt
                    val dur = args.get("duration")?.asInt ?: 300
                    val ok = DeviceController.swipe(x1, y1, x2, y2, dur)
                    "Swiped ($x1, $y1) -> ($x2, $y2) -> success: $ok"
                }
                "type_text" -> {
                    val text = args.get("text").asString
                    val ok = DeviceController.inputText(text)
                    "Typed text \"$text\" -> success: $ok"
                }
                "press_key" -> {
                    val key = args.get("key").asString
                    val ok = DeviceController.pressKeyByName(key)
                    "Pressed key $key -> success: $ok"
                }
                "open_app" -> {
                    val app = args.get("app_name").asString
                    val ok = DeviceController.openApp(app, context)
                    "Opened app $app -> success: $ok"
                }
                "shell_command" -> {
                    val cmd = args.get("command").asString
                    val result = ShizukuManager.runCommand(cmd)
                    "Shell command '$cmd' exit: ${result.exitCode}, stdout: ${result.stdout.take(200)}"
                }
                else -> "Unknown tool: $name"
            }
        } catch (e: Exception) {
            "Tool execution error: ${e.message}"
        }
    }

    private fun buildRequestBody(contents: JsonArray): JsonObject {
        val root = JsonObject()

        // System Instruction
        val sysInstruction = JsonObject().apply {
            val parts = JsonArray().apply {
                val p = JsonObject().apply {
                    addProperty(
                        "text",
                        """
                        You are Open Jarvis, an autonomous AI Phone Automation Agent running directly on an Android device via Shizuku.
                        You control the phone by inspecting the screen image and calling tools:
                        - tap(x, y): Tap on icons, buttons, search bars.
                        - swipe(x1, y1, x2, y2, duration): Scroll up/down or swipe left/right.
                        - type_text(text): Enter search queries, messages, or text into focused input fields.
                        - press_key(key): Press HOME, BACK, RECENTS, ENTER, VOLUME_UP, VOLUME_DOWN.
                        - open_app(app_name): Launch applications by name (e.g. 'youtube', 'chrome', 'settings', 'whatsapp', 'telegram').
                        - shell_command(command): Execute a custom ADB shell command if needed.
                        - finish(summary): Call this when the user's goal has been completely achieved.
                        
                        RULES:
                        1. Always look carefully at the screenshot to locate UI elements.
                        2. Estimate (x, y) coordinates accurately based on the image dimensions.
                        3. Be decisive and efficient: do one clear step at a time.
                        4. When done, call finish(summary) or write your final response clearly.
                        """.trimIndent()
                    )
                }
                add(p)
            }
            add("parts", parts)
        }
        root.add("system_instruction", sysInstruction)
        root.add("contents", contents)

        // Tools Declaration
        val toolsArray = JsonArray()
        val functionDecls = JsonArray().apply {
            add(createFn("tap", "Tap on a point on the screen", listOf("x" to "INTEGER", "y" to "INTEGER")))
            add(createFn("swipe", "Swipe or scroll between two points", listOf("x1" to "INTEGER", "y1" to "INTEGER", "x2" to "INTEGER", "y2" to "INTEGER")))
            add(createFn("type_text", "Type text into currently focused input field", listOf("text" to "STRING")))
            add(createFn("press_key", "Press hardware/system key (HOME, BACK, RECENTS, ENTER, POWER)", listOf("key" to "STRING")))
            add(createFn("open_app", "Open an installed Android application", listOf("app_name" to "STRING")))
            add(createFn("shell_command", "Run raw adb shell command via Shizuku", listOf("command" to "STRING")))
            add(createFn("finish", "Declare the goal finished and provide final summary", listOf("summary" to "STRING")))
        }

        val toolObj = JsonObject().apply { add("function_declarations", functionDecls) }
        toolsArray.add(toolObj)
        root.add("tools", toolsArray)

        val genConfig = JsonObject().apply {
            addProperty("temperature", 0.1)
        }
        root.add("generationConfig", genConfig)

        return root
    }

    private fun createFn(name: String, desc: String, params: List<Pair<String, String>>): JsonObject {
        return JsonObject().apply {
            addProperty("name", name)
            addProperty("description", desc)
            val paramObj = JsonObject().apply {
                addProperty("type", "OBJECT")
                val props = JsonObject()
                val req = JsonArray()
                for ((pName, pType) in params) {
                    props.add(pName, JsonObject().apply { addProperty("type", pType) })
                    req.add(pName)
                }
                add("properties", props)
                add("required", req)
            }
            add("parameters", paramObj)
        }
    }

    private fun parseGeminiResponse(json: String): Pair<String?, JsonObject?> {
        try {
            val root = JsonParser.parseString(json).asJsonObject
            val candidates = root.getAsJsonArray("candidates") ?: return Pair(null, null)
            if (candidates.size() == 0) return Pair(null, null)

            val content = candidates[0].asJsonObject.getAsJsonObject("content") ?: return Pair(null, null)
            val parts = content.getAsJsonArray("parts") ?: return Pair(null, null)

            for (i in 0 until parts.size()) {
                val part = parts[i].asJsonObject
                if (part.has("functionCall")) {
                    return Pair(null, part.getAsJsonObject("functionCall"))
                }
                if (part.has("text")) {
                    return Pair(part.get("text").asString, null)
                }
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to parse Gemini response", e)
        }
        return Pair(null, null)
    }
}
