plugins {
    id("java-library")
}

group = "dev.sbs"
version = "0.1.0"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven(url = "https://central.sonatype.com/repository/maven-snapshots")
    maven(url = "https://jitpack.io")
}

dependencies {
    // Simplified Annotations
    annotationProcessor(libs.simplified.annotations)

    // Lombok Annotations
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    // SpringDoc OpenAPI (compile-only for optional documentation enrichment)
    compileOnly(libs.springdoc.openapi.common)

    // Tests
    testImplementation(libs.springdoc.openapi.scalar)
    testImplementation(libs.hamcrest)
    testImplementation(libs.junit.jupiter.api)
    testRuntimeOnly(libs.junit.jupiter.engine)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.spring.boot.test)

    // Exported dependencies (available to consumers)
    api("com.github.simplified-dev:gson-extras:master-SNAPSHOT")
    api("com.github.simplified-dev:client:master-SNAPSHOT")
    api(libs.gson)
    api(libs.spring.boot.actuator)
    api(libs.spring.boot.web)
    api(libs.spring.boot.security)
    api(libs.bucket4j.core)
}

tasks {
    test {
        useJUnitPlatform()
    }
}
