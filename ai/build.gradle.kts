plugins {
    id("primitive.android.library")
    id("primitive.android.hilt")
}

android {
    namespace = "com.animaltracking.ai"
}

dependencies {
    implementation(project(":core"))

    // LiteRT (TensorFlow Lite)
    implementation(libs.litert)
    implementation(libs.litert.api)
    implementation(libs.litert.support)
    implementation(libs.litert.gpu)
    implementation(libs.litert.gpu.api)
    
    // Core Dependencies
    implementation(libs.androidx.core.ktx)
}
