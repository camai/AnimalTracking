plugins {
    alias(libs.plugins.primitive.android.library)
    alias(libs.plugins.primitive.android.hilt)
    alias(libs.plugins.primitive.android.library.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.animaltracking.feature.horsetracking"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":feature:camera"))
    
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.material3)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
}
