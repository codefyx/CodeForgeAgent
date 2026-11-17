package com.codeforge.agent.ui.terminal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.StringBuilder

class TerminalViewModel(app: Application) : AndroidViewModel(app) {
    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output

    private var process: Process? = null

    fun start(shell: String = "/system/bin/sh", cwd: String = getApplication<android.app.Application>().filesDir.absolutePath) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pb = ProcessBuilder(shell, "-i")
                pb.directory(File(cwd))
                pb.redirectErrorStream(true)
                process = pb.start()
                val reader = process!!.inputStream.bufferedReader()
                val sb = StringBuilder()
                while (true) {
                    val line = reader.readLine() ?: break
                    sb.append(line).append("\n")
                    _output.value = sb.toString()
                }
            } catch (e: Exception) {
                _output.value = "Error: ${e.message}"
            }
        }
    }

    fun send(input: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                process?.outputStream?.bufferedWriter()?.apply {
                    write(input)
                    if (!input.endsWith("\n")) write("\n")
                    flush()
                }
            } catch (e: Exception) {
                _output.value += "\n[write error: ${e.message}]"
            }
        }
    }

    fun stop() {
        process?.destroy()
        process = null
    }

    override fun onCleared() {
        super.onCleared()
        stop()
    }
}