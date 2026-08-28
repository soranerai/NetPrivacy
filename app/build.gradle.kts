import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.atomicfu)
    alias(libs.plugins.compose.compiler)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { stream ->
            load(stream)
        }
    }
}

val releaseKeystorePath: String? = providers.environmentVariable("ANDROID_KEYSTORE_FILE").orNull
    ?: keystoreProperties.getProperty("storeFile")?.let { path ->
        val fRoot = rootProject.file(path)
        if (fRoot.exists()) fRoot.absolutePath else file(path).takeIf { it.exists() }?.absolutePath
    }
val releaseKeystorePassword: String? = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull
    ?: keystoreProperties.getProperty("password")
val releaseKeyAlias: String? = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull
    ?: keystoreProperties.getProperty("keyAlias")
val releaseKeyPassword: String? = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull
    ?: keystoreProperties.getProperty("password")

android {
    namespace = "dev.soranerai.netprivacy"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.soranerai.netprivacy"
        minSdk = 29
        targetSdk = 35
        versionCode = providers.environmentVariable("VERSION_CODE").orNull?.toIntOrNull() ?: 1
        versionName = providers.environmentVariable("VERSION_NAME").orNull ?: "0.1.0-dev"
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

    if (releaseKeystorePath != null && releaseKeystorePassword != null && releaseKeyAlias != null && releaseKeyPassword != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
        buildTypes.named("release") {
            signingConfig = signingConfigs.getByName("release")
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
            manifest.srcFile("src/netprivacy/AndroidManifest.xml")
            java.setSrcDirs(listOf("src/netprivacy/kotlin"))
            res.setSrcDirs(listOf("src/netprivacy/res"))
            assets.setSrcDirs(listOf("src/netprivacy/assets"))
        }
        getByName("test").java.setSrcDirs(listOf("src/netprivacyTest/kotlin"))
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
