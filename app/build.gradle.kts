import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The owner's own module, when this build has it (see settings.gradle.kts).
val personal: Project? = rootProject.findProject(":personal")

// Kiri's version, written once in gradle.properties (`kiri.version`). The version code follows
// from it, so the two never disagree: 1.4.2 is 10402. A release is tagged `v<version>` and told
// in CHANGELOG.md under the same number.
val kiriVersion: String = providers.gradleProperty("kiri.version").get()
val kiriVersionCode: Int = run {
    val parts = kiriVersion.split('.').map { it.toIntOrNull() }
    require(parts.size == 3 && parts.all { it != null && it in 0..99 }) {
        "kiri.version must be three numbers from 0 to 99, like 0.1.0 (it is \"$kiriVersion\")"
    }
    val (major, minor, patch) = parts.map { it!! }
    major * 10_000 + minor * 100 + patch
}

// A build can add to the version name what tells it apart from the others of the same version,
// like the owner's own builds (`-Pkiri.versionSuffix=-pessoal.3`), so an app store such as
// Obtainium sees each one as new. The version code stays the version's, so such a build still
// installs over any other build of the same version.
val kiriVersionName: String = kiriVersion + providers.gradleProperty("kiri.versionSuffix").getOrElse("")

// The release key never lives in the repository. It comes from the environment (the release
// workflow and scripts/release.sh) or from `keystore.properties` at the root, which git ignores.
// Without it a release build still compiles, unsigned.
val releaseKey: Map<String, String>? = run {
    val fromFile = rootProject.file("keystore.properties").takeIf { it.isFile }?.let { file ->
        Properties().apply { file.inputStream().use { load(it) } }
    }
    fun value(env: String, property: String): String? =
        providers.environmentVariable(env).orNull?.takeIf { it.isNotBlank() }
            ?: fromFile?.getProperty(property)?.takeIf { it.isNotBlank() }
    val store = value("KIRI_RELEASE_STORE", "storeFile") ?: return@run null
    val password = value("KIRI_RELEASE_PASSWORD", "password") ?: return@run null
    mapOf("store" to store, "password" to password, "alias" to (value("KIRI_RELEASE_ALIAS", "alias") ?: "kiri"))
}

android {
    namespace = "io.github.johankrs.kiri"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.johankrs.kiri"
        minSdk = 30
        targetSdk = 35
        versionCode = kiriVersionCode
        versionName = kiriVersionName
    }

    signingConfigs {
        // A debug key kept in the repository, so every test build (local or from CI) is signed
        // the same way and installs over the previous one without losing the launcher's data.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        // The key of the published app. Every release must be signed with this same key, or
        // Android refuses to install it over the one before.
        if (releaseKey != null) {
            create("release") {
                storeFile = rootProject.file(releaseKey.getValue("store"))
                storePassword = releaseKey.getValue("password")
                keyAlias = releaseKey.getValue("alias")
                keyPassword = releaseKey.getValue("password")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        // The build to judge smoothness with: optimized like a release, which a debug build is
        // not, but signed and named like the debug one so it installs over it and keeps its data.
        create("optimized") {
            initWith(getByName("release"))
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    sourceSets.getByName("main") {
        // Without the owner's module, empty stand-ins take the place of what it would add.
        if (personal == null) java.srcDir("src/standalone/kotlin")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

composeCompiler {
    stabilityConfigurationFile = layout.projectDirectory.file("compose-stability.conf")
}

dependencies {
    implementation(project(":core"))
    personal?.let { implementation(it) }

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.kotlinx.coroutines.android)
}
