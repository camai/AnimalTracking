plugins {
    alias(libs.plugins.primitive.android.library)
    alias(libs.plugins.primitive.android.hilt)
}

android {
    namespace = "com.animaltracking.domain"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    implementation(project(":core"))
    testImplementation(libs.junit)
}
