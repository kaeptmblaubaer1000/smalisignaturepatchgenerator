import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    id("org.jetbrains.kotlin.jvm")
    id("com.github.johnrengelman.shadow") version "2.0.4"
}

tasks.withType(CreateStartScripts::class.java).getByName("startScripts") {
    classpath = files("lib/SmaliSignaturePatchGenerator.jar")
}

val transformClassesWithProguard = task("transformClassesWithProguard", proguard.gradle.ProGuardTask::class) {
    injars(tasks["shadowJar"])
    libraryjars("${System.getProperty("java.home")}/lib/rt.jar")
    libraryjars("${System.getProperty("java.home")}/lib/jce.jar")
    libraryjars("${System.getProperty("java.home")}/jmods/java.base.jmod")
    libraryjars("${System.getProperty("java.home")}/jmods/java.compiler.jmod")
    libraryjars("${System.getProperty("java.home")}/jmods/java.logging.jmod")
    libraryjars("${System.getProperty("java.home")}/jmods/java.xml.jmod")
    libraryjars("${System.getProperty("java.home")}/jmods/jdk.unsupported.jmod")
    outjars(buildDir.toPath().resolve("proguard").resolve("SmaliSignaturePatchGenerator.jar"))

    keepclasseswithmembers("""public class com.github.kaeptmblaubaer1000.smalisignaturepatchgenerator.Main {
    public static void main(java.lang.String[]);
} """)
    dontobfuscate()
    target("1.8")
}

application {
    mainClassName = "com.github.kaeptmblaubaer1000.smalisignaturepatchgenerator.Main"
    applicationName = "smali-signature-patch-generator"
}


distributions {
    getByName("main") {
        contents {
            exclude {
                it.name.endsWith(".jar") && it.name != "main-all.jar"
            }


            into("lib") {
                from(tasks["shadowJar"]) {
                    rename {
                        if(it == "main-all.jar") "SmaliSignaturePatchGenerator.jar" else it
                    }
                }
            }
        }
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation(project(":mainlib"))


    implementation(project(":cli"))
}

tasks.withType(KotlinCompile::class.java) {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

java.setSourceCompatibility("1.8")
java.setTargetCompatibility("1.8")
