/*
 * Tencent is pleased to support the open source community by making VasDolly available.
 *
 * Copyright (C) 2018 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License");you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 *
 * https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS,WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.tencent.vasdolly.plugin

import com.android.build.api.variant.ApplicationVariant
import com.tencent.vasdolly.plugin.extension.ChannelConfigExtension
import com.tencent.vasdolly.plugin.extension.RebuildChannelConfigExtension
import com.tencent.vasdolly.plugin.task.ApkChannelPackageTask
import com.tencent.vasdolly.plugin.task.RebuildApkChannelPackageTask
import com.tencent.vasdolly.plugin.util.AndroidComponentsExtensionCompat
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project

/***
 * VasDolly插件
 * https://developer.android.com/studio/build/extend-agp
 */
class VasDollyPlugin : Plugin<Project> {
    companion object {
        const val PROPERTY_CHANNELS = "channels"
        const val PROPERTY_CHANNEL_FILE = "channel_file"
    }

    // 当前project
    private lateinit var project: Project

    // 渠道配置
    private lateinit var channelConfigExt: ChannelConfigExtension
    private lateinit var rebuildConfigExt: RebuildChannelConfigExtension

    //渠道列表
    private var channelInfoList: List<String> = listOf()

    override fun apply(project: Project) {
        this.project = project

        //检查是否为android application
        if (!project.plugins.hasPlugin("com.android.application")) {
            throw GradleException("VasDolly plugin 'com.android.application' must be apply")
        }
        //检查扩展配置（channel/rebuildChannel）
        channelConfigExt =
            project.extensions.create("channel", ChannelConfigExtension::class.java, project)
        rebuildConfigExt = project.extensions.create(
            "rebuildChannel",
            RebuildChannelConfigExtension::class.java,
            project
        )
        
        println("=== DEBUG: Extension configuration ===")
        println("DEBUG: channelConfigExt created = ${channelConfigExt != null}")
        println("DEBUG: channelConfigExt.channelFile = ${channelConfigExt.channelFile?.absolutePath}")

        //获取全局工程中配置的渠道列表（gradle.properties文件指定渠道属性）
        channelInfoList = getChannelList()

        //添加扩展渠道任务
        createChannelTask()
        // 注释掉 reBuildChannel 任务创建
        // createRebuildChannelTask()
    }

    /***
     * 新建渠道任务
     */
    private fun createChannelTask() {
        val androidComponents =
            AndroidComponentsExtensionCompat.getAndroidComponentsExtension(project)
        androidComponents.onAllVariants { variant ->
            if (variant is ApplicationVariant) {
                val variantName = variant.name.capitalize()
                println("find android build variant name:${variant.name}")
                project.tasks.register("channel$variantName", ApkChannelPackageTask::class.java) {
                    println("=== DEBUG: Registering task channel$variantName ===")
                    println("DEBUG: channelConfigExt = ${channelConfigExt != null}")
                    println("DEBUG: channelConfigExt.channelFile = ${channelConfigExt.channelFile?.absolutePath}")
                    
                    it.variant = variant
                    it.channelExtension = channelConfigExt
                    it.channelList.addAll(channelInfoList)
                    it.mergeExtChannelList = !project.hasProperty(PROPERTY_CHANNELS)
                    
                    println("DEBUG: it.channelExtension = ${it.channelExtension != null}")
                    println("DEBUG: it.channelList = ${it.channelList}")
                    println("DEBUG: it.mergeExtChannelList = ${it.mergeExtChannelList}")
                    
                    // 设置配置缓存兼容的属性
                    it.variantName.set(variant.name)
                    it.applicationId.set(variant.applicationId)
                    it.versionName.set(variant.outputs.first().versionName.orNull ?: "")
                    it.versionCode.set(variant.outputs.first().versionCode.orNull ?: 0)
                    
                    // 设置扩展配置的缓存兼容属性
                    it.channelFilePath.set(channelConfigExt.channelFile?.absolutePath ?: "")
                    it.outputDirPath.set(channelConfigExt.outputDir.absolutePath)
                    it.apkNameFormat.set(channelConfigExt.apkNameFormat)
                    it.buildTimeDateFormat.set(channelConfigExt.buildTimeDateFormat)
                    it.fastMode.set(channelConfigExt.fastMode)
                    it.lowMemory.set(channelConfigExt.lowMemory)
                    
                    // 设置 variant 信息
                    it.variantBuildType.set(variant.buildType)
                    it.variantFlavorName.set(variant.flavorName)
                    
                    // 设置签名配置信息
                    it.enableV1Signing.set(variant.signingConfig?.enableV1Signing?.get() ?: false)
                    it.enableV2Signing.set(variant.signingConfig?.enableV2Signing?.get() ?: true)
                    
                    // 设置 app 信息
                    it.appName.set(project.name)
                    
                    // 设置 APK 文件输出路径（延迟到执行阶段处理）
                    try {
                        it.baseApkFile.fileProvider(project.provider {
                            val apkDir = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.APK).get()
                            variant.artifacts.getBuiltArtifactsLoader().load(apkDir)?.let { builtArtifacts ->
                                project.file(builtArtifacts.elements.first().outputFile)
                            } ?: project.file("${project.layout.buildDirectory.asFile.get()}/outputs/apk/${variant.name}/${project.name}-${variant.name}.apk")
                        })
                    } catch (e: Exception) {
                        println("Warning: Could not configure APK file path, will resolve at execution time: ${e.message}")
                    }
                    
                    it.dependsOn("assemble$variantName")
                }
            }
        }

        //重新生成渠道包
        // project.tasks.register("reBuildChannel", RebuildApkChannelPackageTask::class.java) {
        //     it.mergeExtChannelList = !project.hasProperty(PROPERTY_CHANNELS)
        //     it.channelList.addAll(channelInfoList)
        //     it.rebuildExt = rebuildConfigExt
        // }
    }

    /**
     * 获取gradle.properties中配置的渠道列表
     * 从v2.0.0开始支持添加渠道参数：
     *    gradle rebuildChannel -Pchannels=yingyongbao,gamecenter
     *  这里通过属性channels指定的渠道列表拥有更高的优先级，且和原始的文件方式channel_file是互斥的
     */
    private fun getChannelList(): List<String> {
        val channelList = mutableListOf<String>()
        //检查是否配置channels属性(拥有更高的优先级,一般用于命令行测试用)
        if (project.hasProperty(PROPERTY_CHANNELS)) {
            val channels = project.properties[PROPERTY_CHANNELS] as String
            if (channels.isNotEmpty()) {
                channelList.addAll(channels.split(","))
            }
            if (channelList.isEmpty()) {
                throw InvalidUserDataException("Property(${PROPERTY_CHANNELS}) channel list is empty , please fix it")
            }
            println("get project channel list from `channels` property,channels:$channelList")
        } else if (project.hasProperty(PROPERTY_CHANNEL_FILE)) {
            //检查是否配置channel_file属性
            val channelFilePath = project.properties[PROPERTY_CHANNEL_FILE] as String
            if (channelFilePath.isNotEmpty()) {
                val channelFile = project.rootProject.file(channelFilePath)
                if (channelFile.exists() && channelFile.isFile) {
                    channelFile.forEachLine { channel ->
                        channelList.add(channel)
                    }
                }
            }
            println("get project channel list from `channel_file` property,file:$channelFilePath,channels:$channelList")
        }
        return channelList
    }
}
