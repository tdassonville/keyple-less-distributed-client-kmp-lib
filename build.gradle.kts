plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.dokka)
    id("com.diffplug.spotless")
    id("maven-publish")
}

kotlin {
    jvmToolchain(libs.versions.jdk.get().toInt())

    if (System.getProperty("os.name").lowercase().contains("mac")) {
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
    }

    androidTarget {
        publishLibraryVariants("release", "debug")
    }

    jvm {
        kotlin {
            jvmToolchain(libs.versions.jdk.get().toInt())
        }
    }

    sourceSets {
        commonMain.dependencies {

            api(libs.keypleless.reader.nfcmobile.kmp.lib)

            implementation(libs.kotlinx.coroutines)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.auth)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)

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
    namespace = "org.eclipse.keyple.keypleless.distributed.client"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }
}

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
    dokkaHtml {
        outputDirectory.set(layout.buildDirectory.dir("dokka"))

        dokkaSourceSets {
            configureEach {
                includeNonPublic.set(false)
                skipDeprecated.set(true)
                reportUndocumented.set(true)
                jdkVersion.set(libs.versions.jdk.get().toInt())
            }
        }
    }

    spotless {
        kotlin {
            target("**/*.kt")
            ktfmt()
            licenseHeaderFile("${project.rootDir}/LICENSE_HEADER")
        }
    }
}
