package ro.amadya

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import ro.amadya.notifications.internal.PushMessage
import ro.amadya.notifications.internal.PushSender
import java.util.concurrent.CopyOnWriteArrayList

/** Captures push notifications instead of calling Expo. */
class RecordingPushSender : PushSender {
    val sent = CopyOnWriteArrayList<PushMessage>()
    override fun send(messages: List<PushMessage>) {
        sent.addAll(messages)
    }
}

@TestConfiguration(proxyBeanMethods = false)
class PushTestConfiguration {
    @Bean
    @Primary
    fun recordingPushSender() = RecordingPushSender()
}
