[![license](http://img.shields.io/badge/license-BSD3-brightgreen.svg?style=flat)](https://github.com/Tencent/VasDolly/blob/master/LICENSE)
[![Release Version](https://img.shields.io/badge/release-3.0.9-red.svg)](https://github.com/yibaoshan/VasDolly-Gradle8-Up/releases)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/Tencent/VasDolly/pulls)
[![wiki](https://img.shields.io/badge/Wiki-open-brightgreen.svg)](https://github.com/Tencent/VasDolly/wiki)
---


# 简介

VasDolly是一种快速多渠道打包工具，同时支持基于V1签名和V2,V3签名进行多渠道打包。插件本身会自动检测Apk使用的签名类别，并选择合适的多渠道打包方式，对使用者来说完全透明。

目前Gradle Plugin 2.2以上默认开启V2签名，所以如果想关闭V2签名，可将下面的v2SigningEnabled设置为false。

官方传送门 https://github.com/Tencent/VasDolly

# 使用说明

### helper 组件

工程根目录 gradle/libs.versions.toml 文件，增加

```
[versions]
...
vasDolly = "v3.0.9-beta"

[libraries]
...
vasdolly-helper = { group = "com.github.yibaoshan.VasDolly-Gradle8-Up", name = "helper", version.ref = "vasDolly" }
```

app 模块 build.gradle.kts 文件，增加

```
dependencies {
    ...
    implementation(libs.vasdolly.helper)
}
```

### plugin 插件

工程根目录 build.gradle.kts 文件，增加

```
buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
    dependencies {
        classpath("com.github.yibaoshan.VasDolly-Gradle8-Up:plugin:v3.0.9-beta") // here
    }
}
plugins {
    ...
}
```

工程根目录 setting.gradle.kts 文件，增加

```

pluginManagement {
    repositories {
        ...
        maven { url = uri("https://jitpack.io") }
    }

    // here
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.yibaoshan.vasdolly") {
                useModule("com.github.yibaoshan.VasDolly-Gradle8-Up:plugin:v3.0.9-beta")
            }
        }
    }
}

dependencyResolutionManagement {
    ...
}

plugins {
    ...
}

include(":xxx")
```

最后，在 app 模块 build.gradle.kts 文件，增加

```
plugins {
    alias(xxx)
    ...
    id("com.yibaoshan.vasdolly")
}
```

# 插件配置

官方传送门 https://github.com/Tencent/VasDolly