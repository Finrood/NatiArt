buildscript {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        // Placed on the buildscript classpath so that `apply(plugin = ...)` and
        // `configure<SpotlessExtension>` resolve in Kotlin DSL.
        classpath("com.diffplug.spotless:spotless-plugin-gradle:8.10.1")
    }
}

plugins {
    java
    id("org.springframework.boot") version "4.1.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.github.ben-manes.versions") version "0.61.0" apply false
}

group = "com.portcelana.natiart"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    // Apply the plugins to subprojects
    apply(plugin = "java")
    apply(plugin = "org.springframework.boot")
    apply(plugin = "io.spring.dependency-management")
    apply(plugin = "com.diffplug.spotless")

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(25)
        }
    }

    // Spotless: same house style as the EMAsphere platform.
    // palantirJavaFormat handles indentation (4 spaces), line length (120),
    // and brace style (K&R). removeUnusedImports + importOrder keep imports
    // tidy. toggleOffOn() enables `// spotless:off` / `// spotless:on`
    // escape hatches for hand-formatted blocks.
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        // Setup-only: keep Spotless out of the `check` lifecycle until the tree is formatted
        // (the follow-up formatting PR flips this to true).
        setEnforceCheck(true)
        java {
            palantirJavaFormat("2.97.0")
            removeUnusedImports()
            // 5 groups, internal packages last (mirrors EMAsphere's
            // java|javax,jakarta,org,com,com.emasphere)
            importOrder("java|javax", "jakarta", "org", "com", "com.portcelana|com.saas")
            toggleOffOn()
            // Excluded patterns (match EMAsphere's pom.xml):
            // build output and generated sources are skipped automatically by
            // the Java source set; no QueryDSL `*Q*` exclude is added because
            // NatiArt has hand-written files ending in `Q` (e.g.
            // PaymentPixQrCodeResponse) that must NOT be ignored.
        }
    }

    dependencies {
        implementation("org.springframework.boot:spring-boot-starter")
        testImplementation("org.springframework.boot:spring-boot-starter-test")
        testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}