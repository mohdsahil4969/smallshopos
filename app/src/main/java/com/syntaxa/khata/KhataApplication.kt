package com.syntaxa.khata

import android.app.Application
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class KhataApplication : Application() {
    lateinit var container: KhataAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        setupCrashHandler()
        container = KhataAppContainer(this)
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stackTrace = sw.toString()
                Log.e("SyntaxaKhataCrash", "CRASH DETECTED on thread ${thread.name}: $stackTrace")

                val crashFile = File(filesDir, "crash_log.txt")
                crashFile.writeText("Time: ${System.currentTimeMillis()}\nThread: ${thread.name}\nException:\n$stackTrace")
            } catch (e: Throwable) {
                Log.e("SyntaxaKhataCrash", "Failed to write crash log: ${e.message}")
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }
}
