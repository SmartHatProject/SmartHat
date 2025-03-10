plugins {
    id("com.android.application")
    alias(libs.plugins.jetbrains.kotlin.android)

}

android {
    namespace = "com.team12.smarthat"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.team12.smarthat"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    
    lint {
        abortOnError = false
        baseline = file("lint-baseline.xml")
        
        // Disable unused resources check for now
        disable += "UnusedResources"
        
        // Generate HTML and XML reports
        htmlOutput = file("lint-results.html")
        xmlReport = true
        
        // Make MissingPermission issues more severe
        error += "MissingPermission"
        warning += "HandlerLeak"
        
        // Check all code including tests
        checkAllWarnings = true
    }
}

dependencies {
    // Core Android
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.viewmodel.android)

    // Room Database
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    annotationProcessor("androidx.room:room-compiler:$room_version")

    // Lifecycle components
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")

    // Java 8+ desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}