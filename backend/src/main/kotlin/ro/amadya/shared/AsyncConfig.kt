package ro.amadya.shared

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * Executor for asynchronous event listeners (@ApplicationModuleListener).
 *
 * Boot's default with virtual threads is unbounded, and every listener holds a database connection, so a burst of
 * orders could take the whole pool and starve requests. A fixed number of workers with a queue bounds that without
 * ever blocking the publisher: a listener that publishes further events (as most do) only enqueues them.
 * Queued events are also recorded in the event publication registry, so a restart does not lose them.
 */
@Configuration
class AsyncConfig {
    @Bean(name = ["applicationTaskExecutor", "taskExecutor"])
    fun applicationTaskExecutor(@Value("\${amadya.event-workers:4}") workers: Int) = ThreadPoolTaskExecutor().apply {
        corePoolSize = workers
        maxPoolSize = workers
        setThreadNamePrefix("event-")
        setVirtualThreads(true)
        setWaitForTasksToCompleteOnShutdown(true)
        setAwaitTerminationSeconds(20)
    }
}
