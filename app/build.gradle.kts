plugins {
    alias(libs.plugins.primitive.android.application)
    alias(libs.plugins.primitive.android.application.compose)
    alias(libs.plugins.primitive.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.jg.animaltracking" // MainActivity 리팩터링을 피하기 위해 기존 네임스페이스 유지

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
    implementation(project(":core:core-android"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":ai"))
    implementation(project(":feature:horse-tracking"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    
    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
