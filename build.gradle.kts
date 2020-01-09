//import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "1.3.61"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation("org.osgi:osgi.cmpn:7.0.0")
    implementation("org.jetbrains.kotlin:kotlin-jdk8-osgi-bundle:1.3.61")

    implementation("io.ktor:ktor-server-netty:1.2.6")
    implementation("org.slf4j:slf4j-jdk14:1.7.28")
    implementation("org.jetbrains.exposed:exposed:0.17.7")
    implementation("com.h2database:h2:1.4.190")
    implementation("org.json:json:20190722")

    implementation(kotlin("stdlib-jdk8"))
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}

//val compileKotlin: KotlinCompile by tasks
//compileKotlin.kotlinOptions {
//    jvmTarget = "1.8"
//}
//val compileTestKotlin: KotlinCompile by tasks
//compileTestKotlin.kotlinOptions {
//    jvmTarget = "1.8"
//}