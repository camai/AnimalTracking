plugins {
    alias(libs.plugins.primitive.android.library)
    alias(libs.plugins.primitive.android.hilt)
}

android {
    namespace = "com.animaltracking.data"
}

dependencies {
    implementation(project(":core:core-android"))
    implementation(project(":core:core-tracking"))
    implementation(project(":domain"))
    implementation(project(":ai")) // AI 모듈 의존성 추가

    implementation(libs.androidx.core.ktx)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
}
