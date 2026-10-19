plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val testJavaToolchains = extensions.getByType<org.gradle.jvm.toolchain.JavaToolchainService>()

android {
    namespace = "org.yanjing.app"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "org.yanjing.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 6
        versionName = "1.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true; aidl = true }
    // Models are copied to private storage and indexes use streams, so both can be compressed.
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        // Reduce the universal APK download; Android extracts only the device's ABI on install.
        jniLibs.useLegacyPackaging = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Robolectric's Android 16 runtime requires Java 21; app bytecode remains Java 17.
            it.javaLauncher.set(testJavaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(21)) })
            it.maxHeapSize = "2g"
            val testTemp = rootProject.layout.buildDirectory.dir("test-tmp").get().asFile.apply { mkdirs() }
            it.systemProperty("java.io.tmpdir", testTemp.absolutePath)
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            val cachedSdk = rootProject.file(".tools/robolectric/android-all-instrumented-15-robolectric-13954326-i7.jar")
            val cachedAndroid16 = rootProject.file(".tools/robolectric/android-all-instrumented-16-robolectric-13921718-i7.jar")
            if (cachedSdk.isFile && cachedSdk.length() == 200643142L && cachedAndroid16.isFile) {
                it.systemProperty("robolectric.offline", "true")
                it.systemProperty("robolectric.dependency.dir", cachedSdk.parentFile.absolutePath)
            }
            it.jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.net=ALL-UNNAMED", "--add-opens=java.base/java.security=ALL-UNNAMED", "--add-opens=java.base/java.text=ALL-UNNAMED", "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED")
        }
    }
    buildTypes {
        release {
            // Preserve the original 1.0 installation identity so updates retain user data.
            signingConfig = signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    lint { checkReleaseBuilds = true }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    // M3 Expressive's public components are available in the official 1.5 beta.
    implementation("androidx.compose.material3:material3:1.5.0-beta01")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    // 1.23.2 misdetects ARM SME on SM8850/Android 16 (upstream #27884).
    // The first Maven line after the confirmed 1.24.4 native fix is 1.25.x.
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.25.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation(platform("androidx.compose:compose-bom:2026.09.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test.ext:junit:1.3.0")
    testImplementation("com.microsoft.onnxruntime:onnxruntime:1.25.1")
}

configurations.matching { it.name.contains("UnitTest", ignoreCase = true) }.configureEach {
    exclude(group = "com.microsoft.onnxruntime", module = "onnxruntime-android")
}
