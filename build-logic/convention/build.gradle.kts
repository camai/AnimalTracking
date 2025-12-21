plugins {
    `kotlin-dsl`
}

group = "com.animaltracking.buildlogic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.compose.compiler) 
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "primitive.android.application"
            implementationClass = "com.animaltracking.convention.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "primitive.android.library"
            implementationClass = "com.animaltracking.convention.AndroidLibraryConventionPlugin"
        }
        register("androidHilt") {
            id = "primitive.android.hilt"
            implementationClass = "com.animaltracking.convention.AndroidHiltConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "primitive.android.application.compose"
            implementationClass = "com.animaltracking.convention.AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "primitive.android.library.compose"
            implementationClass = "com.animaltracking.convention.AndroidLibraryComposeConventionPlugin"
        }
    }
}
