plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.android.gms.oss-licenses-plugin")
}


android {
    namespace = "com.airsky.browser"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.airsky.browser"
        minSdk = 26
        targetSdk = 34
        versionCode = 12
        versionName = "4.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // GeckoView のネイティブライブラリ(libxul.so)は 1 ABI で 100MB を超えるため、
    // ABI ごとに APK を分け、ライブラリも圧縮して格納する。
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // ネイティブのスタートページ(Jetpack Compose + ガラス表現)
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.viewpager:viewpager:1.1.0")
    implementation("com.google.zxing:core:3.3.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("androidx.preference:preference:1.2.1")
    implementation ("androidx.webkit:webkit:1.14.0")
    // 他のアプリのサインイン等で開く簡易ブラウザ(Custom Tabs)の提供側
    implementation("androidx.browser:browser:1.8.0")
    implementation("org.mozilla.geckoview:geckoview:153.0.20260810162159")
    implementation ("androidx.startup:startup-runtime:1.2.0")
    implementation ("androidx.emoji2:emoji2:1.5.0")
    implementation ("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation ("net.lingala.zip4j:zip4j:2.11.5")
    implementation ("com.airbnb.android:lottie:6.0.0")
    implementation("app.rive:rive-android:9.13.9")
    implementation(libs.dagger)
    implementation(libs.hilt.core)
    // CalculatorEditText の ImmutableMap 用(以前は dagger-compiler 経由で混入していたため明示する)
    implementation("com.google.guava:guava:33.3.1-android")
    implementation(files("libs\\arity-2.1.2.jar"))

    // JAR 実行ツール: JAR を端末の上で DEX に変換する D8(Android SDK build-tools 36.0.0 の d8.jar)と、
    // AWT / Swing の参照を互換レイヤーへ付け替える ASM
    implementation(files("libs/d8.jar"))
    implementation("org.ow2.asm:asm:9.7.1")
    implementation("org.ow2.asm:asm-commons:9.7.1")

    // スタートページ: Jetpack Compose とガラス表現(io.github.kyant0:backdrop)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.backdrop)


}
