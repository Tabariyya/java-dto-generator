import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
    }
}

/*
 * FieldConstants is compiled from the Maven module's source tree rather than pulled from the
 * published artifact. The naming rule, the path format and every diagnostic string live in that one
 * file, and the editor has to agree with the compiler on all three; depending on a released version
 * would let the two drift apart for as long as it took to publish one.
 */
sourceSets {
    main {
        java {
            srcDir("../src/main/java")
            include("com/tabariyya/dtogenerator/fields/FieldConstants.java")
            include("com/tabariyya/dtogenerator/idea/**")
        }
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        intellijIdea("2026.2.1")
        bundledPlugin("com.intellij.java")
        testFramework(TestFrameworkType.Platform)
        testFramework(TestFrameworkType.Plugin.Java)
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "262"
            untilBuild = provider { null }
        }
    }
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}
