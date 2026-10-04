package ro.amadya

import org.junit.jupiter.api.Test
import org.springframework.modulith.core.ApplicationModules
import org.springframework.modulith.docs.Documenter

class ModularityTests {

    private val modules = ApplicationModules.of(AmadyaApplication::class.java)

    /** Fails when a module reaches into another module's internals or modules form a cycle (ADR 0001). */
    @Test
    fun `module boundaries are respected`() {
        modules.verify()
    }

    /** Generates C4 component diagrams (PlantUML) and module canvases into build/spring-modulith-docs. */
    @Test
    fun `write module documentation`() {
        Documenter(modules).writeDocumentation()
    }
}
