plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

android {
  namespace = "com.mandala.net"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  androidResources {
    localeFilters.addAll(setOf("en", "id"))
  }

  defaultConfig {
    applicationId = "com.mandala.app"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    ndk {
      abiFilters.addAll(setOf("armeabi-v7a", "arm64-v8a"))
    }
  }

  signingConfigs {
    create("release") {
      // Load from .env file or system environment variables
      val envMap = mutableMapOf<String, String>()
      val envFile = file("${rootDir}/.env")
      if (envFile.exists()) {
        try {
          envFile.forEachLine { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
              val parts = trimmed.split("=", limit = 2)
              if (parts.size == 2) {
                envMap[parts[0].trim()] = parts[1].trim()
              }
            }
          }
        } catch (e: Exception) {
          e.printStackTrace()
        }
      }
      
      val keystorePath = envMap["RELEASE_KEYSTORE_PATH"] ?: System.getenv("RELEASE_KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = envMap["RELEASE_STORE_PASSWORD"] ?: System.getenv("RELEASE_STORE_PASSWORD") ?: "android"
      keyAlias = envMap["RELEASE_KEY_ALIAS"] ?: System.getenv("RELEASE_KEY_ALIAS") ?: "upload"
      keyPassword = envMap["RELEASE_KEY_PASSWORD"] ?: System.getenv("RELEASE_KEY_PASSWORD") ?: "android"
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("GOOGLE_SERVICE_ACCOUNT_KEY")
}

android {
  packaging {
    jniLibs {
      excludes += setOf("**/x86/**", "**/x86_64/**")
    }
  }
  splits {
    abi {
      isEnable = false
      reset()
      include("armeabi-v7a", "arm64-v8a")
      isUniversalApk = true
    }
  }
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // Commented out unused Firebase BOM since no Firebase services are used
  // implementation(platform(libs.firebase.bom))
  // Commented out unused mapping library (WebView/Leaflet is used instead)
  // implementation("org.osmdroid:osmdroid-android:6.1.18")
  implementation("org.maplibre.gl:android-sdk:11.8.0")
  implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // Commented out unused CameraX dependencies
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.vico.compose)
  implementation(libs.vico.compose.m3)
  implementation(libs.vico.core)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // Commented out unused WorkManager dependency
  // implementation("androidx.work:work-runtime-ktx:2.9.0")
  // Commented out unused DiskLruCache dependency
  // implementation("com.jakewharton:disklrucache:2.0.2")
  // Commented out unused Coil image loading library
  // implementation(libs.coil.compose)
  // Commented out unused Moshi serialization converter and library
  // implementation(libs.converter.moshi)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // Commented out unused OkHttp logging interceptor
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // Commented out unused SwipeRefreshLayout dependency
  // implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
  implementation(libs.play.services.location)
  // Commented out massive unused LibVLC media player dependency (saves ~50MB+)
  // implementation("org.videolan.android:libvlc-all:3.5.1")
  // implementation(libs.androidx.media3.exoplayer)
  // implementation(libs.androidx.media3.exoplayer.rtsp)
  // implementation(libs.androidx.media3.ui)
  // Commented out unused Retrofit HTTP client
  // implementation(libs.retrofit)
  // Commented out unused core splashscreen API
  // implementation("androidx.core:core-splashscreen:1.0.1")
  // Commented out unused ML Kit Text Recognition dependency
  // implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.0")
  // Commented out unused Play Services coroutines wrapper
  // implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // Commented out unused Moshi code generator
  // "ksp"(libs.moshi.kotlin.codegen)
}
