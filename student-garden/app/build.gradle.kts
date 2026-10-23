plugins {
 id("com.android.application")
 id("org.jetbrains.kotlin.android")
 id("org.jetbrains.kotlin.plugin.compose")
 id("org.jetbrains.kotlin.plugin.serialization")
}
android {
 namespace = "com.jdinohaila.studentgarden"
 compileSdk = 36
 defaultConfig {
  applicationId = "com.jdinohaila.studentgarden"
  minSdk = 26
  targetSdk = 36
  versionCode = 11
  versionName = "0.11.0"
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  manifestPlaceholders["appLabel"] = "@string/app_name"
 }
 buildTypes {
  debug { applicationIdSuffix = ".preview"; manifestPlaceholders["appLabel"] = "@string/app_name_preview" }
  release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") }
  create("preview") {
   initWith(getByName("release"))
   applicationIdSuffix = ".preview"
   manifestPlaceholders["appLabel"] = "@string/app_name_preview"
   signingConfig = signingConfigs.getByName("debug")
   matchingFallbacks += listOf("release")
  }
 }
 compileOptions {
  sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17
  isCoreLibraryDesugaringEnabled = true
 }
 buildFeatures { compose = true }
 testOptions { unitTests.isIncludeAndroidResources = true; unitTests.all { it.maxHeapSize = "2g"; it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2") } }
 packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
 coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")
 implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5") {
  // The extractor calls Rhino's interpreter directly; Android has no JSR-223 engine.
  exclude(group = "org.mozilla", module = "rhino-engine")
 }
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("androidx.media3:media3-exoplayer:1.11.1")
 implementation("androidx.media3:media3-exoplayer-hls:1.11.1")
 implementation("androidx.media3:media3-ui:1.11.1")
 implementation("androidx.media3:media3-datasource-okhttp:1.11.1")
 implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
 val bom = platform("androidx.compose:compose-bom:2026.03.00")
 implementation(bom); androidTestImplementation(bom); testImplementation(bom)
 implementation("androidx.core:core-ktx:1.17.0")
 implementation("androidx.appcompat:appcompat:1.7.1")
 implementation("androidx.activity:activity-compose:1.12.4")
 implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
 implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
 implementation("androidx.compose.ui:ui")
 implementation("androidx.compose.ui:ui-tooling-preview")
 implementation("androidx.compose.material3:material3")
 implementation("androidx.compose.material:material-icons-extended:1.7.8")
 implementation("androidx.navigation3:navigation3-runtime:1.0.1")
 implementation("androidx.navigation3:navigation3-ui:1.0.1")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
 implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
 implementation("io.coil-kt.coil3:coil-compose:3.3.0")
 implementation("com.mohamedrejeb.richeditor:richeditor-compose:1.0.0-rc14")
 debugImplementation("androidx.compose.ui:ui-tooling")
 debugImplementation("androidx.compose.ui:ui-test-manifest")
 testImplementation("junit:junit:4.13.2")
 testImplementation("org.robolectric:robolectric:4.16.1")
 testImplementation("androidx.test:core:1.7.0")
 testImplementation("androidx.compose.ui:ui-test-junit4")
 androidTestImplementation("androidx.test.ext:junit:1.3.0")
 androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
