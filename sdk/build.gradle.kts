plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.maven.publish)
}

android {
    namespace = "io.esimplified.sdk"
    compileSdk = libs.versions.android.sdk.compile.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.sdk.min.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization.converter)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.timber)
    implementation(libs.koin.core)
    implementation(libs.security.crypto)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}

mavenPublishing {
    coordinates("io.github.esimplified", "android-sdk", "1.0.4")

    pom {
        name.set("eSIMplified Android SDK")
        description.set("Android SDK for eSIM provisioning and management")
        url.set("https://github.com/eSimplified/esimplified-android-sdk")

        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            developer {
                id.set("esimplified")
                name.set("eSimplified")
                email.set("kieranwoodrow@gmail.com")
            }
        }
        scm {
            connection.set("scm:git:git://github.com/eSimplified/esimplified-android-sdk.git")
            developerConnection.set("scm:git:ssh://github.com/eSimplified/esimplified-android-sdk.git")
            url.set("https://github.com/eSimplified/esimplified-android-sdk")
        }
    }

    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL, automaticRelease = true)
    signAllPublications()
}
