plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.pedro.tone3000m1"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.pedro.tone3000m1"

        minSdk = 27
        targetSdk = 37

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++20"
            }
        }
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility =
            JavaVersion.VERSION_11

        targetCompatibility =
            JavaVersion.VERSION_11
    }

    externalNativeBuild {
        cmake {
            path =
                file("src/main/cpp/CMakeLists.txt")

            version =
                "3.22.1"
        }
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.datastore.preferences)

    implementation(
        libs.androidx.appcompat
    )

    implementation(
        libs.androidx.constraintlayout
    )

    implementation(
        libs.androidx.core.ktx
    )

    implementation(
        libs.material
    )

    implementation(
        "androidx.browser:browser:1.10.0"
    )

    testImplementation(
        libs.junit
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        libs.androidx.junit
    )
}
