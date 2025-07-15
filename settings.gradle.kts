rootProject.name = "keyple-interop-jsonapi-client-kmp-lib"

pluginManagement {
  repositories {
    gradlePluginPortal()
    mavenCentral()
    google()
  }
}

dependencyResolutionManagement {
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
  repositories {
    mavenLocal()
    mavenCentral()
    google()
    maven(url = "https://central.sonatype.com/repository/maven-snapshots")
  }
}
