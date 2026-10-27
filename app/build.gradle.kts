
plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.mal.creamwrapper"

    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    buildFeatures {
        viewBinding = true
    }

    defaultConfig {
        applicationId = "com.mal.creamwrapper"
        minSdk = 23
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.lottie)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.material)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    implementation("io.github.pangu-immortal:keeplive-framework:2.0.1")
    implementation("org.bouncycastle:bcprov-jdk18on:1.86")

}
