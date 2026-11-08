import java.net.URI
import java.security.MessageDigest

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// 离线识别模型：首次构建时自动从 Vosk 官方模型库下载到 assets
val voskModelUrl = (findProperty("voskModelUrl") as String?)
    ?: "https://alphacephei.com/vosk/models/vosk-model-small-cn-0.22.zip"
val voskModelSize = 43_898_754L
val voskModelSha256 = (findProperty("voskModelSha256") as String?)
    ?: "3af8b0e7e0f835ae9d414ce5df580237a3cfb08d586c9fbbb0f7ff29ad5b14ba"
val voskModelFile = layout.projectDirectory.file("src/main/assets/model-cn.zip")

fun File.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().use { input ->
        val buffer = ByteArray(1 shl 16)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

tasks.register("downloadVoskModel") {
    description = "下载离线中文语音模型 vosk-model-small-cn-0.22（约 42MB）"
    val target = voskModelFile.asFile
    outputs.file(target)
    onlyIf { !target.exists() || target.length() != voskModelSize }

    doLast {
        target.parentFile.mkdirs()
        val temp = File(target.parentFile, "${target.name}.part")
        temp.delete()
        println("正在下载 Vosk 模型：$voskModelUrl")
        URI(voskModelUrl).toURL().openStream().use { input ->
            temp.outputStream().use { output ->
                val buffer = ByteArray(128 * 1024)
                var downloaded = 0L
                var lastReport = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    downloaded += read
                    if (downloaded - lastReport >= 4 * 1024 * 1024) {
                        lastReport = downloaded
                        println("  已下载 ${downloaded / 1024 / 1024}MB / ${voskModelSize / 1024 / 1024}MB")
                    }
                }
            }
        }
        val actual = temp.sha256()
        if (actual != voskModelSha256) {
            temp.delete()
            throw GradleException(
                "模型校验失败：期望 $voskModelSha256，实际 $actual\n" +
                    "如官方地址已更新，可用 -PvoskModelUrl=... -PvoskModelSha256=... 覆盖。"
            )
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        println("模型已就绪：${target.absolutePath}")
    }
}

tasks.named("preBuild").configure { dependsOn("downloadVoskModel") }

android {
    namespace = "com.audiokit.catchphrase"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.audiokit.catchphrase"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // 模型 zip 已是压缩格式，避免 aapt 二次压缩并保证 openFd 可用
    androidResources {
        noCompress += "zip"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.vosk.android)
    implementation("net.java.dev.jna:jna:5.13.0@aar")

    debugImplementation(libs.androidx.compose.ui.tooling)
}
