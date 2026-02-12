package com.github.gotify.api

import com.github.gotify.Settings
import com.github.gotify.client.ApiClient
import com.github.gotify.client.api.ApplicationApi
import com.github.gotify.client.api.MessageApi
import com.github.gotify.client.model.ApplicationParams
import com.github.gotify.client.model.Message
import org.tinylog.kotlin.Logger

internal class SmsForwarder(private val settings: Settings) {

    fun forwardSms(message: String) {
        val url = settings.url
        val clientToken = settings.token

        if (url.isEmpty() || clientToken.isNullOrEmpty()) {
            Logger.warn("SmsForwarder: Missing configuration (URL or Client Token)")
            return
        }

        try {
            Logger.info("SmsForwarder: Initializing Client API")
            val clientApiClient = ApiClient("clientTokenHeader", clientToken)
            clientApiClient.adapterBuilder.baseUrl(url)
            CertUtils.applySslSettings(clientApiClient.okBuilder, settings.sslSettings())

            val appApi = clientApiClient.createService(ApplicationApi::class.java)
            
            Logger.info("SmsForwarder: Fetching applications")
            val appsResponse = appApi.apps.execute()
            if (!appsResponse.isSuccessful) {
                Logger.error("SmsForwarder: Failed to fetch apps. Code: ${appsResponse.code()}")
                return
            }

            val apps = appsResponse.body() ?: emptyList()
            var appToken = apps.find { it.name == "SMS Forwarder" }?.token

            if (appToken == null) {
                Logger.info("SmsForwarder: 'SMS Forwarder' app not found. Creating new one...")
                val newAppParams = ApplicationParams()
                newAppParams.name("SMS Forwarder")
                newAppParams.description("Auto-generated for SMS Forwarding")
                
                val createAppResponse = appApi.createApp(newAppParams).execute()
                if (!createAppResponse.isSuccessful) {
                    Logger.error("SmsForwarder: Failed to create app. Code: ${createAppResponse.code()}")
                    return
                }
                appToken = createAppResponse.body()?.token
                if (appToken != null) {
                     Logger.info("SmsForwarder: App created successfully")
                }
            } else {
                Logger.info("SmsForwarder: Found existing 'SMS Forwarder' app")
            }

            if (appToken == null) {
                Logger.error("SmsForwarder: Failed to obtain App Token")
                return
            }

            Logger.info("SmsForwarder: Sending message")
            val messageApiClient = ApiClient("appTokenHeader", appToken)
            messageApiClient.adapterBuilder.baseUrl(url)
            CertUtils.applySslSettings(messageApiClient.okBuilder, settings.sslSettings())

            val messageApi = messageApiClient.createService(MessageApi::class.java)
            val msg = Message()
            msg.message = message
            msg.title = "SMS Forwarding"
            msg.priority = 5

            val response = messageApi.createMessage(msg).execute()
            
            if (response.isSuccessful) {
                Logger.info("SmsForwarder: Message sent successfully")
            } else {
                Logger.error("SmsForwarder: Failed to send message. Code: ${response.code()}")
            }

        } catch (e: Exception) {
            Logger.error(e, "SmsForwarder: Error processing SMS forwarding")
        }
    }
}
