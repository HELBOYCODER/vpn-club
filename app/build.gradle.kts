plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.helboy.vpnclub"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.helboy.vpnclub"
        minSdk = 29
        targetSdk = 35
        versionCode = 49
        versionName = "4.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // امضای ثابت: بدون آن هر بیلد CI با یک کلید debug تصادفی امضا می‌شد و نسخه‌ی جدید
    // روی نسخه‌ی قبلی نصب نمی‌شد. کلید از Secretهای گیت‌هاب (HELLBOY_KEYSTORE_*) می‌آید.
    val hbKeystore = System.getenv("HELLBOY_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (hbKeystore != null) {
            create("hellboy") {
                storeFile = hbKeystore
                storePassword = System.getenv("HELLBOY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("HELLBOY_KEY_ALIAS") ?: "hellboyvpn"
                keyPassword = System.getenv("HELLBOY_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (providers.gradleProperty("hellboyPreview").orNull == "true") applicationIdSuffix = ".preview43"
            isMinifyEnabled = false
            signingConfig = if (hbKeystore != null) signingConfigs.getByName("hellboy")
                            else signingConfigs.getByName("debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            if (providers.gradleProperty("hellboyPreview").orNull == "true") applicationIdSuffix = ".preview43"
            isMinifyEnabled = false
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":vpnLib"))

    // هسته‌ی PattNG: Xray + hev-socks5-tunnel
    implementation(files("libs/libv2ray.aar"))
    implementation(files("libs/hev-htproxy.aar"))

    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")

    // OkHttp for fetching VPNGate mirror feeds
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // QR کد برای اشتراک کانفیگ (فقط هسته‌ی جاوا، بدون دوربین)
    implementation("com.google.zxing:core:3.5.3")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
