plugins {
    alias(libs.plugins.primitive.android.library)
    alias(libs.plugins.primitive.android.hilt)
}

android {
    namespace = "com.animaltracking.core.android"
}

dependencies {
    implementation(project(":core:core-tracking"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.camera.core)
    implementation(project(":domain"))
}
