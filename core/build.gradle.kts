import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin: no Android types here, so everything in this module is testable on the JVM.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Android ships org.json, so it is only needed to compile here and to run the tests.
    compileOnly(libs.json)
    testImplementation(libs.json)
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}
