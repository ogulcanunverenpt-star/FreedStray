import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val envProperties = Properties().apply {
    val envFile = file("/home/ogulcan/Desktop/freestray/osman-esra-c2/server/.env")
    if (envFile.exists()) {
        envFile.inputStream().use { load(it) }
    }
}

val serverHost = envProperties.getProperty("SERVER_IP", "156.67.27.88")
val httpPort = envProperties.getProperty("HTTP_PORT", "3000").toIntOrNull() ?: 3000
val tcpPort = envProperties.getProperty("TCP_PORT", "8000").toIntOrNull() ?: 8000
val aesKey = envProperties.getProperty("AES_KEY", "2f8d4c5a7e8b1f23d9a1c0b8d67f3f4c6a2b4d1e9c8f7a5d1c2b3e4f8a7d6c5b")

android {
    namespace = "com.godot.freedstray"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.godot.freedstray"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SERVER_HOST", "\"$serverHost\"")
        buildConfigField("int", "HTTP_PORT", "$httpPort")
        buildConfigField("int", "TCP_PORT", "$tcpPort")
        buildConfigField("String", "AES_KEY", "\"$aesKey\"")

        externalNativeBuild {
            cmake {
                cppFlags("")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.8")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")

    // Retrofit & OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
