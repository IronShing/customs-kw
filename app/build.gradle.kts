import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

val signingProps = Properties().apply {
    val f = file(System.getProperty("user.home") + "/android/keys/signing.properties")
    if (f.exists()) f.inputStream().use(::load)
}

android {
    namespace = "com.ironshing.customs"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.ironshing.customs"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "1.1"
    }
    if (signingProps.getProperty("KEYSTORE_FILE", "").isNotEmpty()) {
        signingConfigs {
            create("release") {
                storeFile = file(signingProps.getProperty("KEYSTORE_FILE"))
                storePassword = signingProps.getProperty("KEYSTORE_PASSWORD")
                keyAlias = signingProps.getProperty("KEY_ALIAS")
                keyPassword = signingProps.getProperty("KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.activity.compose)
    testImplementation(libs.junit)
}
