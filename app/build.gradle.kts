plugins {
    id("com.android.application")
    alias(libs.plugins.jetbrains.kotlin.android)

}

android {
    namespace = "com.team12.smarthat"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.team12.smarthat"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
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
    
    // Configure test options for unit tests
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            
            // Use JUnit 4 instead of JUnit 5 platform since our tests are written in JUnit 4
            all {
                it.useJUnit()
            }
        }
    }
}

// Add force resolution for Mockito dependencies
configurations.all {
    resolutionStrategy {
        force("org.mockito:mockito-core:5.4.0")
        force("org.mockito:mockito-inline:5.2.0")
        force("net.bytebuddy:byte-buddy:1.14.10")
        force("net.bytebuddy:byte-buddy-agent:1.14.10")
    }
}

dependencies {
    // Core Android
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation(libs.lifecycle.viewmodel.android)
    
    // SplashScreen API backward compatibility
    implementation("androidx.core:core-splashscreen:1.0.1")
    
    // Preferences support
    implementation("androidx.preference:preference:1.2.1")
    
    // Room Database
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    annotationProcessor("androidx.room:room-compiler:$room_version")

    // Lifecycle components - updated for better Android 12 compatibility
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-service:2.6.2")

    // Java 8+ desugaring
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    // Testing - JUnit
    testImplementation("junit:junit:4.13.2")
    
    // Update Mockito configurations
    testImplementation("org.mockito:mockito-core:5.4.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("net.bytebuddy:byte-buddy:1.14.10")
    testImplementation("net.bytebuddy:byte-buddy-agent:1.14.10")
    
    // Robolectric for Android framework in unit tests
    testImplementation("org.robolectric:robolectric:4.10.3")
    
    // androidx test for LiveData testing
    testImplementation("androidx.arch.core:core-testing:2.2.0") // LiveData testing
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test:runner:1.5.2")
    testImplementation("androidx.test.ext:junit:1.1.5")
    
    // hamcrest for better assertions
    testImplementation("org.hamcrest:hamcrest:2.2")
    
    // AndroidX test libraries for instrumented tests
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("org.mockito:mockito-android:5.4.0")

    //SpeedView
    implementation("com.github.anastr:SpeedView:1.5.2")
}