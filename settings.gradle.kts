pluginManagement {
    repositories {
        // 优先走国内镜像（阿里云），公网直连在国内常被 TLS 掐断；
        // 镜像里没有的包再回落到官方源。
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
include(":app")

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/public")
        maven("https://maven.aliyun.com/repository/google")
        google()
        mavenCentral()
        maven("https://api.xposed.info/")
        maven("https://s01.oss.sonatype.org/content/repositories/releases/")
    }
}
