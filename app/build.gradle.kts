plugins {
    alias(libs.plugins.primitive.android.application)
    alias(libs.plugins.primitive.android.application.compose)
    alias(libs.plugins.primitive.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.jg.animaltracking" // Keeping existing namespace to avoid refactoring MainActivity immediately

    defaultConfig {
        applicationId = "com.jg.animaltracking"
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":ai"))
    implementation(project(":feature:tracking"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
}