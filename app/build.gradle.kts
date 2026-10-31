plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ir.mineblox.app"

    // همه فایل‌های کدنویسی Kotlin در یک پوشه ساده نگهداری می‌شوند.
    sourceSets.getByName("main").java.srcDirs("src/main/code")
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.mineblox.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            // نسخه اول برای تست است؛ امضای Release را بعداً با کلید اختصاصی تنظیم می‌کنیم.
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
}

dependencies {
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.core:core-ktx:1.15.0")
}
