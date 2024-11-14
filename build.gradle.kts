plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    id("org.eclipse.keyple")
    id("com.diffplug.spotless")
    id("maven-publish")
}

kotlin {
    jvmToolchain(17)

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "keyplelessdistributedlib"
            isStatic = false
        }
    }

    androidTarget {
        publishLibraryVariants("release", "debug")
    }

    jvm {
        kotlin {
            jvmToolchain(17)
        }
    }

    sourceSets {
        commonMain.dependencies {

            api(libs.keypleLessReaderNfcmobile)

            implementation(libs.kotlinx.coroutines)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

            implementation(libs.okio)

            api(libs.napier)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        jvmMain.dependencies {
            implementation(libs.ktor.client.cio)
        }
    }
}

android {
    namespace = "org.eclipse.keyple.keyplelessdistributedlib"
    compileSdk = 34

    defaultConfig {
        minSdk = 23
        targetSdk = 34
    }
}

group = "org.eclipse.keyple"
version = "0.1.2"

publishing {
    repositories {
        mavenLocal {
            //...
        }
    }
}

///////////////////////////////////////////////////////////////////////////////
//  TASKS CONFIGURATION
///////////////////////////////////////////////////////////////////////////////
tasks {
    spotless {
        kotlin {
            target("**/*.kt")
            ktfmt()
            licenseHeaderFile("${project.rootDir}/LICENSE_HEADER")
        }
    }
}
