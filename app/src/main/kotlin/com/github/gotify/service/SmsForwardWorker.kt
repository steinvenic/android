package com.github.gotify.service

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.github.gotify.Settings
import com.github.gotify.api.SmsForwarder
import org.tinylog.kotlin.Logger

class SmsForwardWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val message = inputData.getString("message") ?: return Result.failure()
        return try {
            val settings = Settings(applicationContext)
            val forwarder = SmsForwarder(settings)
            forwarder.forwardSms(message)
            Result.success()
        } catch (e: Exception) {
            Logger.error(e, "SmsForwardWorker: Failed to forward SMS")
            Result.retry()
        }
    }
}
