plugins {
    id("primitive.android.library")
    id("primitive.android.hilt")
}

android {
    namespace = "com.animaltracking.core"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.camera.core)
}
