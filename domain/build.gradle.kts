plugins {
    id("primitive.android.library")
    id("primitive.android.hilt")
}

android {
    namespace = "com.animaltracking.domain"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":core"))
}
