plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.extrive.vigilex"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.extrive.vigilex"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.retrofit.core)
    implementation(libs.retrofit.kotlinx.serialization.converter)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
// A physical phone cannot use the emulator's 10.0.2.2 alias. After every debug build,
// forward the phone's localhost:8000 to this computer's port 8000 (FastAPI) over USB,
// which is what ApiConfig.USB_REVERSE_BASE_URL relies on. Harmless when no device is
// attached: the exit code is ignored. Run it by hand with ./gradlew adbReverseDevServer.
val adbReverseDevServer = tasks.register<Exec>("adbReverseDevServer") {
    group = "vigilex"
    description = "Forwards tcp:8000 on the connected phone to tcp:8000 on this computer."
    executable = androidComponents.sdkComponents.adb.get().asFile.absolutePath
    args("reverse", "tcp:8000", "tcp:8000")
    isIgnoreExitValue = true
}
tasks.matching { it.name == "assembleDebug" || it.name == "installDebug" }.configureEach {
    finalizedBy(adbReverseDevServer)
}
