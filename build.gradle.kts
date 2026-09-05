plugins {
    java
}

group = "com.karlo"
version = "2.3.0"

repositories {
    mavenCentral()

    maven {
        name = "papermc"
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        name = "placeholderapi"
        url = uri("https://repo.helpch.at/releases")
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }

    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
    compileOnly("me.clip:placeholderapi:2.11.7")
}

sourceSets {
    main {
        resources.srcDir("src/main/resources")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
}

configurations.named("compileClasspath") {
    attributes {
        attribute(
            org.gradle.api.attributes.java.TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
            25
        )
    }
}

tasks.jar {
    archiveBaseName.set("OrionFFACore")
    archiveVersion.set("2.3.0-Paper-26.2")
}