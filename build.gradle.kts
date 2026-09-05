plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
}

group = "com.karlo"
version = "3.0.0"

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
    maven("https://repo.helpch.at/releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.48-alpha")
    compileOnly("com.sk89q.worldedit:worldedit-bukkit:7.4.5")
    compileOnly("me.clip:placeholderapi:2.12.3")
    implementation("com.mysql:mysql-connector-j:9.4.0")
    implementation("com.zaxxer:HikariCP:7.1.0")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(25)
    options.encoding = "UTF-8"
}

tasks.processResources {
    filesMatching("plugin.yml") { expand("version" to project.version) }
}

tasks.register<JavaExec>("reservationCheck") {
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.karlo.orionffa.arena.ArenaReservationCheck")
    enableAssertions = true
}

tasks.withType<Test>().configureEach {
    // ReservationCheck is a dedicated executable guard; there are currently no JUnit tests.
    failOnNoDiscoveredTests.set(false)
}

tasks.check {
    dependsOn("reservationCheck")
}

tasks.shadowJar {
    archiveClassifier.set("")
    mergeServiceFiles()
    relocate("com.mysql", "com.karlo.orionffa.libs.mysql")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
