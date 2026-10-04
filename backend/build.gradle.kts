import org.openapitools.generator.gradle.plugin.tasks.GenerateTask

plugins {
    val kotlinVersion = "2.3.21"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.spring") version kotlinVersion
    kotlin("plugin.jpa") version kotlinVersion
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.25.0"
    jacoco
}

group = "ro.amadya"
version = "0.1.0"

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

repositories { mavenCentral() }

extra["springModulithVersion"] = "2.1.1"

dependencyManagement {
    imports {
        mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jdbc")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.stripe:stripe-java:34.0.0")
    implementation("io.minio:minio:9.0.3")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.awaitility:awaitility-kotlin")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// ---------------------------------------------------------------- OpenAPI (contract-first, ADR 0002)
val generatedDir = layout.buildDirectory.dir("generated/openapi")

val openApiGenerate by tasks.existing(GenerateTask::class) {
    generatorName = "kotlin-spring"
    inputSpec = rootProject.file("../api/openapi/openapi.yaml")
    outputDir = generatedDir
    apiPackage = "ro.amadya.contract.api"
    modelPackage = "ro.amadya.contract.model"
    cleanupOutput = true
    configOptions = mapOf(
        "interfaceOnly" to "true",
        "skipDefaultInterface" to "true",
        "useTags" to "true",
        "useSpringBoot3" to "true",
        "useJakartaEe" to "true",
        "useBeanValidation" to "true",
        "documentationProvider" to "none",
        "annotationLibrary" to "none",
        "enumPropertyNaming" to "UPPERCASE",
        "exceptionHandler" to "false",
        "gradleBuildFile" to "false",
        "sourceFolder" to "src/main/kotlin",
    )
}

sourceSets["main"].kotlin.srcDir(generatedDir.map { it.dir("src/main/kotlin") })

tasks.named("compileKotlin") { dependsOn(openApiGenerate) }

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
}
