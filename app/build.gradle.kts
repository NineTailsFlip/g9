import java.util.Properties

plugins {
    id("com.android.application")
    kotlin("android")
}

val keystoreProperties = Properties()
val keystorePropertiesFile = project.file("keystore.properties")
val hasKeystore = keystorePropertiesFile.exists()
if (hasKeystore) keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }

// G9 — the phone's photos and videos on 9 keys, in the Nine suite's look. Shares its MediaStore
// reading, list rows and zoomable picture with TG9's "send photo or video" screen through :kit
// (nine-core/kit, a git submodule).
android {
    namespace = "dev.g9"
    compileSdk = 34

    defaultConfig {
        applicationId = "io.github.ninetailsflip.g9"
        minSdk = 34
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    if (hasKeystore) {
        signingConfigs {
            create("release") {
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // As TG9: a release build is much faster on a keypad phone than a debug one, and the
            // debug key lets it install over a debug build.
            signingConfig = if (hasKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
            vcsInfo { include = false }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        // Like every Nine app: English-only, screens built in code from string literals.
        disable += "SetTextI18n"
        // targetSdk 34 is deliberate: the Nine suite targets exactly the Android 14 keypad phones
        // it's tested on. Lint only flags it where a newer platform is installed (CI runners).
        disable += "OldTargetApi"
        warningsAsErrors = true
        abortOnError = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":update"))
    implementation(project(":kit"))
    testImplementation("junit:junit:4.13.2")
}
