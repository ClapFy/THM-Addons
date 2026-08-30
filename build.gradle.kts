/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

plugins {
    alias(libs.plugins.fabric.loom)
}

data class McFlavor(
    val minecraft: String,
    val fabricApi: String,
    val meteor: String,
    val baritone: String,
    val minecraftDepends: String,
    val compat: String,
)

val flavors = mapOf(
    "26.1.1" to McFlavor(
        minecraft = "26.1.1",
        fabricApi = "0.145.4+26.1.1",
        meteor = "26.1.2-SNAPSHOT",
        baritone = "26.1-SNAPSHOT",
        minecraftDepends = "26.1.1",
        compat = "26.1",
    ),
    "26.1.2" to McFlavor(
        minecraft = "26.1.2",
        fabricApi = "0.155.2+26.1.2",
        meteor = "26.1.2-SNAPSHOT",
        baritone = "26.1-SNAPSHOT",
        minecraftDepends = "~26.1",
        compat = "26.1",
    ),
    "26.2" to McFlavor(
        minecraft = "26.2",
        fabricApi = "0.154.2+26.2",
        meteor = "26.2-SNAPSHOT",
        baritone = "26.2-SNAPSHOT",
        minecraftDepends = "~26.2",
        compat = "26.2",
    ),
)

val mcTarget = providers.gradleProperty("mc").orElse("26.2").get()
val flavor = flavors[mcTarget] ?: error(
    "Unknown -Pmc=$mcTarget. Use one of: ${flavors.keys.joinToString()}"
)

val jdkVersion = libs.versions.jdk.get()
val modVersion = libs.versions.mod.version.get()

val archivesBaseName = providers.gradleProperty("archives_base_name").get()
val mavenGroup = providers.gradleProperty("maven_group").get()

base {
    archivesName = archivesBaseName
    version = "$modVersion+$mcTarget"
    group = mavenGroup
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(jdkVersion.toInt()))
    }
}

repositories {
    maven {
        name = "meteor-maven"
        url = uri("https://maven.meteordev.org/releases")
    }
    maven {
        name = "meteor-maven-snapshots"
        url = uri("https://maven.meteordev.org/snapshots")
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${flavor.minecraft}")
    implementation(libs.fabric.loader)
    implementation("net.fabricmc.fabric-api:fabric-api:${flavor.fabricApi}")
    implementation("meteordevelopment:meteor-client:${flavor.meteor}")
    compileOnly("meteordevelopment:baritone:${flavor.baritone}")
}

val generateAPIUtils = tasks.register("generateAPIUtils") {
    val secretsFile = file("secrets.properties")
    val secretsExampleFile = file("secrets.properties.example")
    // Falls back to the example (placeholder example.com URLs) so contributors without the
    // real secrets.properties can still build - real API calls just won't resolve to anything.
    val activeSecretsFile = if (secretsFile.exists()) secretsFile else secretsExampleFile
    val outputFile = file("src/main/java/xyz/thm/addon/utils/GeneratedApiEndpoints.java")

    inputs.file(activeSecretsFile)
    outputs.file(outputFile)

    doLast {
        file("src/main/java/xyz/thm/addon/utils/password.java").delete()
        if (!activeSecretsFile.exists()) error(
            "Neither secrets.properties nor secrets.properties.example found."
        )
        if (activeSecretsFile == secretsExampleFile) {
            logger.lifecycle("secrets.properties not found - building with placeholder URLs from secrets.properties.example. Copy it to secrets.properties and fill in real URLs for working API calls.")
        }

        val props = Properties()
        activeSecretsFile.bufferedReader(Charsets.UTF_8).use<java.io.Reader, Unit> { props.load(it) }

        fun req(k: String) = props.getProperty(k) ?: error("secrets.properties missing key: $k")

        val urls = linkedMapOf(
            "memberHud" to req("api.memberHud"),
            "highway" to req("api.highway"),
            "status" to req("api.status"),
            "highwayStatus" to req("api.highwayStatus"),
            "capeList" to req("api.cape"),
            "capePost" to req("api.capePost"),
            "capeIndex" to req("api.capeIndex")
        )
        for ((name, url) in urls) {
            if (!url.startsWith("https://")) {
                error("secrets.properties key api.$name must be an https:// URL")
            }
        }

        val rng = SecureRandom()
        val password = MessageDigest.getInstance("SHA-256")
            .digest(("thm-local-stats-v1\n" + urls.values.joinToString("\n")).toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        val masterKey = MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))
        val keyA = ByteArray(32).also { rng.nextBytes(it) }
        val keyB = ByteArray(32) { (masterKey[it].toInt() xor keyA[it].toInt()).toByte() }
        val passwordBytes = password.toByteArray(Charsets.UTF_8)
        val passwordMask = ByteArray(passwordBytes.size).also { rng.nextBytes(it) }
        val passwordMasked = ByteArray(passwordBytes.size) { (passwordBytes[it].toInt() xor passwordMask[it].toInt()).toByte() }

        fun encryptUrl(url: String): String {
            val iv = ByteArray(12).also { rng.nextBytes(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(masterKey, "AES"), GCMParameterSpec(128, iv))
            val packed = iv + cipher.doFinal(url.toByteArray(Charsets.UTF_8))
            return Base64.getEncoder().encodeToString(packed)
        }

        fun ba(bytes: ByteArray) = bytes.joinToString(",") { it.toInt().toString() }

        val encrypted = urls.mapValues { encryptUrl(it.value) }

        outputFile.parentFile.mkdirs()
        outputFile.writeText(
            """
            |package xyz.thm.addon.utils;
            |
            |import javax.crypto.Cipher;
            |import javax.crypto.spec.GCMParameterSpec;
            |import javax.crypto.spec.SecretKeySpec;
            |import java.nio.charset.StandardCharsets;
            |import java.util.Base64;
            |
            |/**
            | * Build-generated vault for API endpoint URLs and the local stats-artifact password.
            | * Method names and HTTP behaviour live in {@link APIUtils}; only ciphertext is here.
            | */
            |final class GeneratedApiEndpoints {
            |    private static final byte[] KEY_A = {${ba(keyA)}};
            |    private static final byte[] KEY_B = {${ba(keyB)}};
            |    private static final byte[] PASSWORD_MASKED = {${ba(passwordMasked)}};
            |    private static final byte[] PASSWORD_MASK = {${ba(passwordMask)}};
            |    private static final String MEMBER_HUD = "${encrypted["memberHud"]}";
            |    private static final String HIGHWAY = "${encrypted["highway"]}";
            |    private static final String STATUS = "${encrypted["status"]}";
            |    private static final String HIGHWAY_STATUS = "${encrypted["highwayStatus"]}";
            |    private static final String CAPE_LIST = "${encrypted["capeList"]}";
            |    private static final String CAPE_POST = "${encrypted["capePost"]}";
            |    private static final String CAPE_INDEX = "${encrypted["capeIndex"]}";
            |
            |    private GeneratedApiEndpoints() {}
            |
            |    static String localPassword() {
            |        byte[] out = new byte[PASSWORD_MASKED.length];
            |        for (int i = 0; i < out.length; i++) out[i] = (byte) (PASSWORD_MASKED[i] ^ PASSWORD_MASK[i]);
            |        return new String(out, StandardCharsets.UTF_8);
            |    }
            |
            |    static String memberHudUrl() { return decrypt(MEMBER_HUD); }
            |    static String highwayUrl() { return decrypt(HIGHWAY); }
            |    static String statusUrl() { return decrypt(STATUS); }
            |    static String highwayStatusUrl() { return decrypt(HIGHWAY_STATUS); }
            |    static String capeListUrl() { return decrypt(CAPE_LIST); }
            |    static String capePostUrl() { return decrypt(CAPE_POST); }
            |    static String capeIndexUrl() { return decrypt(CAPE_INDEX); }
            |
            |    private static byte[] masterKey() {
            |        byte[] key = new byte[32];
            |        for (int i = 0; i < 32; i++) key[i] = (byte) (KEY_A[i] ^ KEY_B[i]);
            |        return key;
            |    }
            |
            |    private static String decrypt(String packed) {
            |        try {
            |            byte[] raw = Base64.getDecoder().decode(packed);
            |            if (raw.length < 13) return null;
            |            byte[] iv = new byte[12];
            |            byte[] ciphertext = new byte[raw.length - 12];
            |            System.arraycopy(raw, 0, iv, 0, 12);
            |            System.arraycopy(raw, 12, ciphertext, 0, ciphertext.length);
            |            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            |            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKey(), "AES"), new GCMParameterSpec(128, iv));
            |            String url = new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
            |            if (!url.startsWith("https://")) return null;
            |            return url;
            |        } catch (Exception e) {
            |            return null;
            |        }
            |    }
            |}
            """.trimMargin(),
            Charsets.UTF_8
        )
        logger.lifecycle("Generated GeneratedApiEndpoints.java (URL ciphertext only; APIUtils stays readable)")
    }
}

sourceSets {
    main {
        java {
            srcDir("src/compat/${flavor.compat}/java")
            exclude("xyz/thm/addon/modules/HandshakeHostTest.java")
            exclude("xyz/thm/addon/mixin/ClientConnectionMixin.java")
            exclude("xyz/thm/addon/mixin/HandshakeC2SPacketMixin.java")
            exclude("xyz/thm/addon/mixin/MultiplayerScreenRouteButtonMixin.java")
            exclude("xyz/thm/addon/mixin/TitleScreenFastestRouteButtonMixin.java")
        }
    }
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to flavor.minecraft,
            "minecraft_version" to flavor.minecraftDepends,
            "jdk_version" to jdkVersion,
            "gh_hash" to (System.getenv("GITHUB_SHA") ?: run {
                val process = ProcessBuilder("git", "rev-parse", "HEAD")
                    .directory(rootDir)
                    .start()
                process.inputStream.bufferedReader().readLine()?.trim() ?: ""
            }),
            "gh_branch" to (System.getenv("GITHUB_REF_NAME") ?: run {
                val process = ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
                    .directory(rootDir)
                    .start()
                process.inputStream.bufferedReader().readLine()?.trim() ?: ""
            }),
        )

        inputs.properties(propertyMap)

        filteringCharset = "UTF-8"

        filesMatching("fabric.mod.json") {
            expand(propertyMap)
        }
    }

    jar {
        inputs.property("archivesName", project.base.archivesName.get())

        exclude("xyz/thm/addon/modules/ElytraUAV.class")
        exclude("xyz/thm/addon/modules/ElytraUAV$*.class")
        exclude("xyz/thm/addon/modules/HandshakeHostTest.class")
        exclude("xyz/thm/addon/modules/HandshakeHostTest$*.class")
        exclude("xyz/thm/addon/mixin/ClientConnectionMixin.class")
        exclude("xyz/thm/addon/mixin/HandshakeC2SPacketMixin.class")
        exclude("xyz/thm/addon/mixin/MultiplayerScreenRouteButtonMixin.class")
        exclude("xyz/thm/addon/mixin/TitleScreenFastestRouteButtonMixin.class")

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }

        manifest {
            attributes("Main-Class" to "xyz.thm.addon.Main")
        }
    }

    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = jdkVersion.toInt()
        options.isFork = true
        options.forkOptions.memoryMaximumSize = "3g"
        options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
        dependsOn(generateAPIUtils)
    }
}

logger.lifecycle("THM Addons target Minecraft ${flavor.minecraft} / Meteor ${flavor.meteor} (compat ${flavor.compat})")
