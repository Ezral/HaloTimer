plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}
android {
    namespace = "com.ezral.halo"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.ezral.halo"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-alpha01"
    }
    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint { abortOnError = true }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
    implementation(project(":core:timer"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material)
    implementation(libs.compose.preview)
    debugImplementation(libs.compose.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.compose)
    implementation(libs.core.ktx)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore)
    implementation(libs.coroutines)
    implementation(libs.serialization)
}
