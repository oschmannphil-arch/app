plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nextlesson.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nextlesson.app"
        minSdk = 26
        targetSdk = 34
        // Jeder CI-Lauf zählt hoch, damit Updates immer als neuere Version gelten.
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0.${System.getenv("GITHUB_RUN_NUMBER") ?: "0"}"
    }

    // Fester Schlüssel für Updates ohne Deinstallieren. Er liegt NICHT im Repo, sondern kommt
    // im CI-Lauf aus den GitHub-Secrets KEYSTORE_BASE64 / KEYSTORE_PASSWORD.
    val keystoreDatei = System.getenv("KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    if (keystoreDatei != null) {
        signingConfigs {
            create("fest") {
                storeFile = keystoreDatei
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = "nextlesson"
                keyPassword = System.getenv("KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Ohne Secrets (z.B. lokaler Build): Standard-Debug-Schlüssel, damit die APK
            // installierbar bleibt – dann aber ohne Update-über-alte-Version.
            signingConfig = signingConfigs.findByName("fest") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    // LifecycleEventEffect (ersetzt das veraltete LocalLifecycleOwner aus compose.ui)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Background refresh
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Secure local storage for credentials
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Homescreen widget
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")

    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
