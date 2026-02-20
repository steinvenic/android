package com.github.gotify.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import com.github.gotify.Settings
import java.util.regex.Pattern
import org.tinylog.kotlin.Logger

class SmsReceiver : BroadcastReceiver() {
    // pattern to extract verification codes (4 to 8 digits)
    // (?<!\d) ensures not preceded by digit
    // \d{4,8} matches 4 to 8 digits
    // (?!\d) ensures not followed by digit
    private val codePattern = Pattern.compile("(?<!\\d)\\d{4,8}(?!\\d)")

    override fun onReceive(context: Context, intent: Intent) {
        Logger.info("SmsReceiver: onReceive triggered. Action: ${intent.action}")

        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            Logger.warn(
                "SmsReceiver: Wrong action, ignoring. Expected: ${Telephony.Sms.Intents.SMS_RECEIVED_ACTION}"
            )
            return
        }

        val settings = Settings(context)
        val isEnabled = settings.smsForwarding
        Logger.info("SmsReceiver: SMS Forwarding enabled: $isEnabled")

        if (!isEnabled) {
            Logger.info("SmsReceiver: SMS Forwarding is disabled in settings, ignoring")
            return
        }

        try {
            Logger.info("SmsReceiver: Extracting messages from intent")
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            Logger.info("SmsReceiver: Extracted ${messages?.size ?: 0} message(s)")

            if (messages == null || messages.isEmpty()) {
                Logger.warn("SmsReceiver: No messages in intent, aborting")
                return
            }

            val fullMessage = StringBuilder()
            var sender = ""

            for (sms in messages) {
                if (sender.isEmpty()) {
                    sender = sms.displayOriginatingAddress ?: "Unknown"
                }
                fullMessage.append(sms.displayMessageBody)
            }

            val msgContent = fullMessage.toString()
            Logger.info("SmsReceiver: Received SMS from $sender. Length: ${msgContent.length}")

            val matcher = codePattern.matcher(msgContent)

            if (matcher.find()) {
                val code = matcher.group()
                val forwardMessage = "SMS from $sender\nCode: $code\n\n$msgContent"
                Logger.info("SmsReceiver: Found code $code from $sender, scheduling forward...")

                val data = Data.Builder().putString("message", forwardMessage).build()
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
                val workRequest = OneTimeWorkRequestBuilder<SmsForwardWorker>()
                    .setInputData(data)
                    .setConstraints(constraints)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build()

                WorkManager.getInstance(context).enqueue(workRequest)
            } else {
                Logger.debug("SmsReceiver: No code found in message from $sender")
            }
        } catch (e: Exception) {
            Logger.error(e, "SmsReceiver: Error processing SMS")
        }
    }
}
