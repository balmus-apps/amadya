package ro.amadya

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.modulith.Modulithic
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@Modulithic(systemName = "Amadya", sharedModules = ["shared", "contract"])
class AmadyaApplication

fun main(args: Array<String>) {
    runApplication<AmadyaApplication>(*args)
}
