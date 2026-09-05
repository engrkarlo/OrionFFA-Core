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
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.+")
}

sourceSets {
    main {
        resources.srcDir("src/main/resources")
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
    options.encoding = "UTF-8"
}

tasks.jar {
    archiveBaseName.set("OrionFFACore")
    archiveVersion.set("2.3.0-Paper-26.2")
}