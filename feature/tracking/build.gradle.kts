plugins {
    id("primitive.android.library")
    id("primitive.android.hilt")
    id("primitive.android.library.compose")
}

android {
    namespace = "com.animaltracking.feature.tracking"
}

dependencies {
    implementation(project(":core"))
    implementation(project(":domain"))
    implementation(project(":data")) // Functionally it uses repository interface, but DI needs impl usually. 
    // In clean architecture, feature -> domain. 
    // Data injection happens at app level usually. 
    // But for simplicity in multi-module without refined DI graph, feature often depends on data for repository impl if not strict.
    // Wait, Hilt binds it. Feature should only depend on Domain and Core for classes. 
    // The App module aggregates them.
    // Removing data dependency from here to be strict.
    
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.material3)
}
