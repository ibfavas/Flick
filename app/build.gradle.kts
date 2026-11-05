plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ibfavas.flick"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ibfavas.flick"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "1.0.4"
    }

    signingConfigs {
        create("release") {
            // Secrets are passed on the command line (-PflickStoreFile=... etc.)
            // and are never committed to the repo.
            val storeFileProp = project.findProperty("flickStoreFile") as String?
            val storePasswordProp = project.findProperty("flickStorePassword") as String?
            val keyAliasProp = project.findProperty("flickKeyAlias") as String?
            val keyPasswordProp = project.findProperty("flickKeyPassword") as String?
            if (storeFileProp != null) {
                storeFile = file(storeFileProp)
                storePassword = storePasswordProp
                keyAlias = keyAliasProp
                keyPassword = keyPasswordProp
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.foundation:foundation-layout")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Note: the gesture library uses plain SQLite (see data/Gesture.kt), not
    // Room, because v1.0.0 was built without Gradle (see README).
}
