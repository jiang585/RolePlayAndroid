pluginManagement {
    repositories {
        // 国内镜像优先（阿里云），官方仓库作为兜底
        maven("https://maven.aliyun.com/repository/gradle-plugin") {
            name = "AliyunGradlePlugin"
        }
        maven("https://maven.aliyun.com/repository/google") {
            name = "AliyunGoogle"
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 国内镜像优先（阿里云），官方仓库作为兜底
        maven("https://maven.aliyun.com/repository/public") {
            name = "AliyunPublic"
        }
        maven("https://maven.aliyun.com/repository/google") {
            name = "AliyunGoogle"
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "RolePlayChat"
include(":app")
 