plugins {
    alias(libs.plugins.primitive.android.library)
    alias(libs.plugins.primitive.android.library.compose)
}

android {
    namespace = "com.animaltracking.feature.camera"
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.ktx)

    api(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
}
