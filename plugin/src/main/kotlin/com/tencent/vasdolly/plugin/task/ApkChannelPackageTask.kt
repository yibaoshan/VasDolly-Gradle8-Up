package com.tencent.vasdolly.plugin.task

import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.variant.ApplicationVariant
import com.tencent.vasdolly.plugin.extension.ChannelConfigExtension
import com.tencent.vasdolly.plugin.util.SimpleAGPVersion
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.provider.Property
import org.gradle.api.file.RegularFileProperty
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

abstract class ApkChannelPackageTask : ChannelPackageTask() {
    // 当前基础apk
    @Internal
    var baseApk: File? = null

    @get:Internal
    @Transient
    var variant: ApplicationVariant? = null
    
    // 配置缓存兼容的 variant 信息
    @get:Input
    abstract val variantBuildType: Property<String>
    
    @get:Input
    abstract val variantFlavorName: Property<String>
    
    // 配置缓存兼容的签名配置信息
    @get:Input
    abstract val enableV1Signing: Property<Boolean>
    
    @get:Input
    abstract val enableV2Signing: Property<Boolean>

    @get:Internal
    @Transient
    var channelExtension: ChannelConfigExtension? = null
    
    // 配置缓存兼容的扩展配置属性
    @get:Input
    abstract val channelFilePath: Property<String>
    
    @get:Input
    abstract val outputDirPath: Property<String>
    
    @get:Input
    abstract val apkNameFormat: Property<String>
    
    @get:Input
    abstract val buildTimeDateFormat: Property<String>
    
    @get:Input
    abstract val fastMode: Property<Boolean>
    
    @get:Input
    abstract val lowMemory: Property<Boolean>
    
    // 配置缓存兼容的 app 信息
    @get:Input
    abstract val appName: Property<String>

    // 配置缓存兼容的属性
    @get:Internal
    abstract val baseApkFile: RegularFileProperty

    @get:Input
    abstract val variantName: Property<String>

    @get:Input
    abstract val applicationId: Property<String>

    @get:Input
    abstract val versionName: Property<String>

    @get:Input
    abstract val versionCode: Property<Int>

    @TaskAction
    fun taskAction() {
        //1.check all params
        checkParameter();
        //2.generate channel apk
        generateChannelApk();
    }

    /***
     * check channel plugin params
     */
    private fun checkParameter() {
        println("=== DEBUG: Task $name checkParameter() ===")

        println("DEBUG: channelFilePath = ${channelFilePath.get()}")
        println("DEBUG: outputDirPath = ${outputDirPath.get()}")
        println("DEBUG: apkNameFormat = ${apkNameFormat.get()}")
        println("DEBUG: buildTimeDateFormat = ${buildTimeDateFormat.get()}")
        println("DEBUG: fastMode = ${fastMode.get()}")
        println("DEBUG: lowMemory = ${lowMemory.get()}")
        
        // 检查扩展配置的详细信息
        if (channelExtension != null) {
            println("DEBUG: channelExtension class = ${channelExtension!!::class.java.name}")
            println("DEBUG: channelExtension.toString() = ${channelExtension.toString()}")
        }
        
        if (mergeExtChannelList) {
            mergeChannelList()
        }
        
        // 尝试从配置缓存兼容的属性获取渠道文件
        val channelFileFromCache = if (channelFilePath.get().isNotEmpty()) {
            File(channelFilePath.get())
        } else {
            channelExtension?.channelFile
        }

        //1.check channel List
        if (channelList.isEmpty()) {
            val channelFile = channelExtension?.channelFile
            val errorMessage = if (channelFile == null) {
                "Task $name channel list is empty. Please check:\n" +
                "1. Set channelFile in channel{} configuration block\n" +
                "2. Or use gradle property: -Pchannels=channel1,channel2"
            } else if (!channelFile.exists()) {
                "Task $name channel list is empty. Channel file does not exist: ${channelFile.absolutePath}\n" +
                "Please create the channel file with channel names (one per line)"
            } else if (!channelFile.isFile) {
                "Task $name channel list is empty. Channel file is not a valid file: ${channelFile.absolutePath}"
            } else {
                "Task $name channel list is empty. Channel file exists but contains no valid channels: ${channelFile.absolutePath}\n" +
                "Please add channel names to the file (one per line)"
            }
            throw InvalidUserDataException(errorMessage)
        }
        println("Task $name, channelList: $channelList")


        //2.check base apk
        if (variant == null) {
            println("DEBUG: variant is null, using cached variant info")
            println("DEBUG: variantBuildType = ${variantBuildType.get()}")
            println("DEBUG: variantFlavorName = ${variantFlavorName.get()}")
        }
        baseApk = getVariantBaseApk() ?: throw RuntimeException("can't find base apk")
        println("Task $name, baseApk: ${baseApk?.absolutePath}")


        //3.check ChannelExtension
        if (channelExtension == null) {
            println("DEBUG: channelExtension is null, using cached extension info")
            println("DEBUG: channelFilePath = ${channelFilePath.get()}")
            println("DEBUG: outputDirPath = ${outputDirPath.get()}")
        } else {
            channelExtension?.checkParams()
            println("Task $name, channel files outputDir:${channelExtension?.outputDir?.absolutePath}")
        }
    }

    @Suppress("PrivateApi")
    private fun getVariantBaseApk(): File? {
        // 优先使用配置缓存兼容的属性（执行阶段获取）
        if (baseApkFile.isPresent) {
            val file = baseApkFile.get().asFile
            if (file.exists()) {
                return file
            }
        }
        
        // 回退到旧的方式（运行时动态获取）
        return variant?.let { variant ->
            try {
                val currentAGPVersion = SimpleAGPVersion.ANDROID_GRADLE_PLUGIN_VERSION
                val agpVersion7 = SimpleAGPVersion(7, 0)
                val apkFolder = if (currentAGPVersion < agpVersion7) {
                    //AGP4.2
                    val artifactCls = Class.forName("com.android.build.api.artifact.ArtifactType")
                    val apkClass =
                        Class.forName("com.android.build.api.artifact.ArtifactType${'$'}APK").kotlin
                    val provider = variant.artifacts.javaClass.getMethod("get", artifactCls)
                        .invoke(variant.artifacts, apkClass.objectInstance) as Provider<Directory>
                    provider.get()
                } else {
                    //AGP7.0
                    variant.artifacts.get(SingleArtifact.APK).get()
                }
                variant.artifacts.getBuiltArtifactsLoader().load(apkFolder)?.let {
                    File(it.elements.first().outputFile)
                }
            } catch (e: Exception) {
                println("Warning: Could not get APK file from variant artifacts: ${e.message}")
                findApkFileByConvention()
            }
        } ?: findApkFileByConvention()
    }

    /**
     * 根据约定查找 APK 文件（最后的回退方案）
     */
    private fun findApkFileByConvention(): File? {
        val buildDir = File(System.getProperty("user.dir"), "build")
        val apkDir = File(buildDir, "outputs/apk/${variantName.get()}")
        
        if (!apkDir.exists()) {
            return null
        }
        
        return apkDir.listFiles { _, name ->
            name.endsWith(".apk") && name.contains(variantName.get())
        }?.firstOrNull()
    }

    /***
     * 根据签名类型生成不同的渠道包
     */
    private fun generateChannelApk() {
        val outputDir = if (channelExtension != null) {
            channelExtension?.outputDir
        } else {
            File(outputDirPath.get())
        }
        println("generateChannelApk baseApk:${baseApk?.absolutePath},outputDir:${outputDir?.path}")
        
        val signingConfig = variant?.signingConfig
        
        // 如果 variant 为 null，使用缓存的签名配置信息
        if (variant == null) {
            println("DEBUG: Using cached signing config info")
            println("DEBUG: enableV1Signing = ${enableV1Signing.get()}")
            println("DEBUG: enableV2Signing = ${enableV2Signing.get()}")
        }
        
        val lowMemory = if (channelExtension != null) {
            channelExtension?.lowMemory ?: false
        } else {
            lowMemory.get()
        }
        
        val isFastMode = if (channelExtension != null) {
            channelExtension?.fastMode ?: false
        } else {
            fastMode.get()
        }
        // 检查签名配置
        val enableV2 = if (signingConfig != null) {
            signingConfig.enableV2Signing.get()
        } else {
            enableV2Signing.get()
        }
        
        val enableV1 = if (signingConfig != null) {
            signingConfig.enableV1Signing.get()
        } else {
            enableV1Signing.get()
        }
        
        println("DEBUG: enableV2Signing = $enableV2")
        println("DEBUG: enableV1Signing = $enableV1")
        
        when {
            enableV2 -> {
                println("DEBUG: Using V2 signing mode")
                generateV2ChannelApk(baseApk!!, outputDir!!, lowMemory, isFastMode)
            }
            enableV1 -> {
                println("DEBUG: Using V1 signing mode")
                generateV1ChannelApk(baseApk!!, outputDir!!, isFastMode)
            }
            else -> {
                throw GradleException("Task $name: No valid signing mode found. V1: $enableV1, V2: $enableV2")
            }
        }
    }

    /***
     * 获取渠道文件名
     */
    override fun getChannelApkName(baseApkName: String, channel: String): String {
        println("=== DEBUG: getChannelApkName for channel: $channel ===")
        
        val localChannelExtension = channelExtension
        val timeFormat = if (localChannelExtension != null && !localChannelExtension.buildTimeDateFormat.isNullOrEmpty()) {
            localChannelExtension.buildTimeDateFormat
        } else {
            buildTimeDateFormat.get()
        }
        println("DEBUG: timeFormat = $timeFormat")
        
        val buildTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern(timeFormat))
        println("DEBUG: buildTime = $buildTime")
        
        val outInfo = variant?.outputs?.first()
        println("DEBUG: outInfo = $outInfo")

        val keyValue: MutableMap<String, String> = mutableMapOf()
        keyValue["appName"] = appName.get()
        keyValue["flavorName"] = channel
        keyValue["buildType"] = variant?.buildType ?: variantBuildType.get()
        keyValue["versionName"] = outInfo?.versionName?.get() ?: versionName.get()
        keyValue["versionCode"] = outInfo?.versionCode?.get().toString() ?: versionCode.get().toString()
        keyValue["appId"] = variant?.applicationId?.get() ?: applicationId.get()
        keyValue["buildTime"] = buildTime
        
        println("DEBUG: keyValue = $keyValue")

        //默认文件名
        val apkNamePrefix = if (localChannelExtension != null && !localChannelExtension.apkNameFormat.isNullOrEmpty()) {
            localChannelExtension.apkNameFormat
        } else {
            apkNameFormat.get()
        }
        println("DEBUG: apkNamePrefix = $apkNamePrefix")
        
        var finalApkName = apkNamePrefix
        keyValue.forEach { (k, v) ->
            finalApkName = finalApkName.replace("${'$'}{" + k + "}", v)
        }
        println("DEBUG: finalApkName = $finalApkName")
        return "$finalApkName.apk"
    }

    /***
     * 获取渠道列表
     */
    override fun getExtensionChannelList(): List<String> {
        println("=== DEBUG: getExtensionChannelList() ===")
        
        // 优先从配置缓存兼容的属性获取
        val channelFileFromCache = if (channelFilePath.get().isNotEmpty()) {
            File(channelFilePath.get())
        } else {
            channelExtension?.channelFile
        }
        
        println("DEBUG: channelFileFromCache = ${channelFileFromCache?.absolutePath}")
        println("DEBUG: channelFileFromCache exists = ${channelFileFromCache?.exists()}")
        
        val channelList = mutableListOf<String>()
        if (channelFileFromCache != null && channelFileFromCache.exists() && channelFileFromCache.isFile) {
            channelFileFromCache.forEachLine { channel ->
                println("DEBUG: Reading line: '$channel'")
                if (channel.isNotEmpty()) {
                    channelList.add(channel)
                    println("DEBUG: Added channel: '$channel'")
                } else {
                    println("DEBUG: Skipped empty line")
                }
            }
            println("DEBUG: Final channelList from cache: $channelList")
        } else {
            println("DEBUG: channelFileFromCache is null or not valid")
            return channelExtension?.getExtensionChannelList() ?: listOf()
        }
        
        return channelList
    }
}