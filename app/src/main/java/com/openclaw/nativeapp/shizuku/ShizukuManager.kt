package com.openclaw.nativeapp.shizuku

import android.content.Context
import android.content.pm.PackageManager
import com.openclaw.nativeapp.data.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.CopyOnWriteArrayList

object ShizukuManager {
    private const val TAG = "ShizukuManager"
    const val REQUEST_CODE_SHIZUKU = 1001

    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    ) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    interface StateListener {
        fun onShizukuStateChanged(isAvailable: Boolean, isGranted: Boolean)
    }

    private val stateListeners = CopyOnWriteArrayList<StateListener>()

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        LogManager.i(TAG, "Shizuku Binder received.")
        notifyState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        LogManager.w(TAG, "Shizuku Binder died.")
        notifyState()
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == REQUEST_CODE_SHIZUKU) {
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            LogManager.i(TAG, "Shizuku permission result: granted=$granted")
            notifyState()
        }
    }

    fun init() {
        try {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(permissionResultListener)
            LogManager.i(TAG, "ShizukuManager initialized.")
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to initialize Shizuku listeners", e)
        }
    }

    fun addListener(listener: StateListener) {
        stateListeners.add(listener)
        listener.onShizukuStateChanged(isAvailable(), isPermissionGranted())
    }

    fun removeListener(listener: StateListener) {
        stateListeners.remove(listener)
    }

    fun notifyState() {
        val available = isAvailable()
        val granted = isPermissionGranted()
        for (listener in stateListeners) {
            try {
                listener.onShizukuStateChanged(available, granted)
            } catch (e: Exception) {
                LogManager.e(TAG, "Listener error", e)
            }
        }
    }

    fun isAvailable(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Throwable) {
            false
        }
    }

    fun isPermissionGranted(): Boolean {
        if (!isAvailable()) return false
        return try {
            if (Shizuku.isPreV11()) {
                false
            } else {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }
        } catch (e: Throwable) {
            false
        }
    }

    fun requestPermission(requestCode: Int = REQUEST_CODE_SHIZUKU) {
        if (!isAvailable()) {
            LogManager.w(TAG, "Cannot request permission: Shizuku service is not running.")
            return
        }
        try {
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Shizuku.requestPermission(requestCode)
            } else {
                LogManager.i(TAG, "Shizuku permission is already granted.")
            }
        } catch (e: Exception) {
            LogManager.e(TAG, "Failed to request Shizuku permission", e)
        }
    }

    suspend fun runCommand(command: String, timeoutMillis: Long = 20_000): CommandResult = withContext(Dispatchers.IO) {
        if (!isAvailable() || !isPermissionGranted()) {
            return@withContext CommandResult(-1, "", "Shizuku is not running or permission is not granted")
        }

        val result = withTimeoutOrNull(timeoutMillis) {
            try {
                // Execute command via Shizuku shell process using reflection for compatibility across API versions
                val process = try {
                    val method = Shizuku::class.java.getMethod(
                        "newProcess",
                        Array<String>::class.java,
                        Array<String>::class.java,
                        String::class.java
                    )
                    method.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
                } catch (e: Exception) {
                    Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                }

                val stdoutBuilder = StringBuilder()
                val stderrBuilder = StringBuilder()

                val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
                val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

                var line: String?
                while (stdoutReader.readLine().also { line = it } != null) {
                    stdoutBuilder.append(line).append("\n")
                }

                while (stderrReader.readLine().also { line = it } != null) {
                    stderrBuilder.append(line).append("\n")
                }

                val exitCode = process.waitFor()
                CommandResult(
                    exitCode = exitCode,
                    stdout = stdoutBuilder.toString().trim(),
                    stderr = stderrBuilder.toString().trim()
                )
            } catch (e: Exception) {
                LogManager.e(TAG, "Command execution failed: $command", e)
                CommandResult(-1, "", e.message ?: "Unknown execution error")
            }
        }

        result ?: CommandResult(-2, "", "Command timed out after ${timeoutMillis / 1000}s")
    }
}
