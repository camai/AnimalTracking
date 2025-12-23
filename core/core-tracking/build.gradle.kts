plugins {
    alias(libs.plugins.primitive.android.library)
    alias(libs.plugins.primitive.android.hilt)
}

android {
    namespace = "com.animaltracking.core.tracking"
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    
    // Testing
    testImplementation(libs.junit)
}