plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.example.spacetycoon"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.example.spacetycoon"
        minSdk = 24
        targetSdk = 34
        versionCode = 5
        versionName = "alpha 5.0 by arabchikby"
    }
    // Один и тот же ключ подписи для всех сборок: иначе каждая сборка в GitHub Actions
    // получает новый случайный ключ и Android не даёт обновить приложение поверх старого.
    signingConfigs {
        create("shared") {
            storeFile = file("spacetycoon.keystore")
            storePassword = "spacetycoon"
            keyAlias = "spacetycoon"
            keyPassword = "spacetycoon"
        }
    }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("shared") }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("shared")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.2")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
}
