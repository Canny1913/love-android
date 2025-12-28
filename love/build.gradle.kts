import com.android.build.gradle.tasks.ExternalNativeBuildTask

plugins {
    id("com.android.library")
}

android {
    // If you want to use the debugger for JNI code, you want to debug this lib-project in Debug!
    // And due to gradle limitations/bugs, the best way is to always debug it in debug.
    namespace = "org.love2d.android"
    ndkVersion = "25.2.9519653"

    defaultConfig {
        minSdk = 18
        compileSdk = 34
        targetSdk = 34

        externalNativeBuild {
            ndkBuild {
                arguments.add("-j${Runtime.getRuntime().availableProcessors()}")
            }
        }

        ndk {
            // noinspection ChromeOsAbiSupport
            val filter = (project.property("abi.filter") as? String) ?: error("ABI filter is required!")
            abiFilters += filter
            debugSymbolLevel = "SYMBOL_TABLE"
        }
    }

    val retrieveAll3pModules = {
        val modules = mutableListOf<String>()

        fileTree("src/jni/lua-modules").visit {
            if (isDirectory) {
                val androidMk = file("${file.path}/Android.mk")
                if (androidMk.exists()) {
                    val logger = project.logger
                    logger.lifecycle("3rd-party module: ${file.path}")

                    val javaInfo = file("${file.path}/java.txt")
                    if (javaInfo.exists()) {
                        val infile = javaInfo.bufferedReader()
                        val javaPath = infile.readLine().replace("\\", "/")

                        val modulePath =
                        if (!javaPath.startsWith("/")) {
                            "${file.path}/$javaPath"
                        } else {
                            "${file.path}$javaPath"
                        }

                        modules += modulePath
                        logger.lifecycle("Registered path $modulePath")

                        infile.close()
                    }
                }
            }
        }

        modules
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(
                    getDefaultProguardFile("proguard-android.txt"),
                    "proguard-rules.pro"
            )
        }

        getByName("debug") {
            ndk {
                // noinspection ChromeOsAbiSupport
                abiFilters += "x86_64"
            }
        }
    }

    flavorDimensions += "mode"

    productFlavors {
        create("normal") {
            dimension = "mode"
        }
        create("embed") {
            dimension = "mode"
        }
    }

    sourceSets {
        getByName("main") {
            java {
                srcDir("src/jni/SDL2/android-project/app/src/main/java")
                srcDir("src/main/java")
                srcDirs(retrieveAll3pModules())
            }
        }

        getByName("normal") {
            java {
                srcDir("src/normal/java")
            }
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = file("src/jni/Android.mk")
        }
    }

    packagingOptions {
        jniLibs {
            excludes += listOf(
                    "lib/arm64-v8a/libSDL2.so",
                    "lib/armeabi-v7a/libSDL2.so",
                    "lib/x86_64/libSDL2.so",
                    "lib/x86/libSDL2.so"
            )
        }
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    api(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    api("androidx.appcompat:appcompat:1.6.1")
}

afterEvaluate {
    tasks.register("makeLibs", Copy::class.java) {
        val externalNativeBuild = tasks.named<ExternalNativeBuildTask>("externalNativeBuildNormalRelease")
        dependsOn += externalNativeBuild
        from(externalNativeBuild.flatMap { it.soFolder })
        destinationDir = File(project.buildDir, "libs")
    }
}