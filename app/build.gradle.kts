import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val studioDebugKey = rootProject.layout.projectDirectory.file(".gradle/mosslight-debug.keystore")
val prepareStudioDebugKey by tasks.registering(Exec::class) {
    outputs.file(studioDebugKey)
    onlyIf { !studioDebugKey.asFile.exists() }
    doFirst { studioDebugKey.asFile.parentFile.mkdirs() }
    val keytool = File(System.getProperty("java.home"), "bin/keytool" + if (System.getProperty("os.name").startsWith("Windows")) ".exe" else "")
    commandLine(keytool.absolutePath, "-genkeypair", "-noprompt", "-keystore", studioDebugKey.asFile.absolutePath,
        "-storepass", "android", "-keypass", "android", "-alias", "androiddebugkey", "-keyalg", "RSA", "-keysize", "2048",
        "-validity", "10000", "-dname", "CN=Mosslight Studio Debug,O=Local Development,C=US")
}

android {
    namespace = "com.ryanhami.mosslightstudio"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.ryanhami.mosslightstudio"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "com.ryanhami.mosslightstudio.StudioTestRunner"
    }
    signingConfigs {
        getByName("debug") {
            storeFile = studioDebugKey.asFile
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        val releasePath = providers.environmentVariable("MOSSLIGHT_STORE_FILE").orNull
        if (releasePath != null) {
            create("studioRelease") {
                storeFile = file(releasePath)
                storePassword = providers.environmentVariable("MOSSLIGHT_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("MOSSLIGHT_KEY_ALIAS").getOrElse("mosslight")
                keyPassword = providers.environmentVariable("MOSSLIGHT_KEY_PASSWORD").orNull
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("studioRelease")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
    packaging.resources {
        // Identical upstream license resources are consolidated; notices are supplied with the app.
        merges += setOf("META-INF/LICENSE*", "META-INF/NOTICE*")
        excludes += setOf("META-INF/DEPENDENCIES")
    }
    testOptions { unitTests.isReturnDefaultValues = true }
    lint { abortOnError = true }
}

tasks.matching { it.name == "validateSigningDebug" }.configureEach { dependsOn(prepareStudioDebugKey) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.03.01")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.datastore:datastore-core:1.2.1")
    implementation("androidx.datastore:datastore-core-okio:1.2.1")
    implementation("com.google.code.gson:gson:2.14.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test:core-ktx:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}

