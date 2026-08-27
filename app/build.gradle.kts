plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.atomicfu)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.soranerai.simhide"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.soranerai.simhide"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-dev"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("src/simhide/AndroidManifest.xml")
            java.setSrcDirs(listOf("src/simhide/kotlin"))
            res.setSrcDirs(listOf("src/simhide/res"))
            assets.setSrcDirs(listOf("src/simhide/assets"))
        }
        getByName("test").java.setSrcDirs(listOf("src/simhideTest/kotlin"))
    }
}

dependencies {
    // Xposed API — compileOnly so it's not bundled into the APK.
    compileOnly("de.robv.android.xposed:api:82")

    // Android 12 SplashScreen API, backported to API 23+.
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Compose UI
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation("androidx.compose.material:material-icons-extended")
    implementation("io.github.oikvpqya.compose.fastscroller:fastscroller-material3:0.3.2")
    implementation("io.github.oikvpqya.compose.fastscroller:fastscroller-indicator:0.3.2")
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation("junit:junit:4.13.2")
    testImplementation(libs.coroutines.test)
}
