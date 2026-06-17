import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

plugins {
    alias(libs.plugins.fabric.loom)
}

base {
    archivesName = properties["archives_base_name"] as String
    version = libs.versions.mod.version.get()
    group = properties["maven_group"] as String
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
    // Fabric
    minecraft(libs.minecraft)
    mappings(variantOf(libs.yarn) { classifier("v2") })
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)
    //IDK why websocket is here tbh
    implementation("org.java-websocket:Java-WebSocket:1.5.4")

    // Meteor
    modImplementation(libs.meteor.client)

    // Baritone
    modCompileOnly(libs.baritone)
}

// Reads plaintext API URLs from secrets.properties, generates a fresh random
// password per build, encrypts the URLs with it (AES-256-ECB, same scheme the
// Java runtime decrypts), and XOR-obfuscates the password in the bytecode.
// Neither the password nor the URLs ever appear as readable strings in the jar.
val generatePasswordClass by tasks.registering {
    val secretsFile = file("secrets.properties")
    val outputFile  = file("src/main/java/xyz/thm/addon/utils/password.java")

    inputs.file(secretsFile)
    outputs.file(outputFile)
    // Always re-run: the random password changes every build so every jar is unique.
    outputs.upToDateWhen { false }

    doLast {
        if (!secretsFile.exists()) error(
            "secrets.properties not found. Copy secrets.properties.example, fill in your API URLs, then rebuild."
        )

        val props = Properties()
        secretsFile.bufferedReader(Charsets.UTF_8).use<java.io.Reader, Unit> { props.load(it) }

        fun req(key: String) = props.getProperty(key)
            ?: error("secrets.properties is missing required key: $key")

        val plainA1 = req("api.memberHud")
        val plainA2 = req("api.highway")
        val plainA3 = req("api.status")
        val plainA4 = req("api.highwayStatus")

        val rng = SecureRandom()

        // Random 32-byte password encoded as hex — never written anywhere, only lives in the bytecode.
        val pwBytes = ByteArray(32).also { rng.nextBytes(it) }
        val password = pwBytes.joinToString("") { "%02x".format(it) }

        // Encrypt a plaintext URL using AES-256-ECB + SHA-256 key — matches ThmMembers.decryptAPI().
        fun encryptUrl(url: String): String {
            val aesKey = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8)), "AES")
            val cipher = Cipher.getInstance("AES")
            cipher.init(Cipher.ENCRYPT_MODE, aesKey)
            return Base64.getEncoder().encodeToString(cipher.doFinal(url.toByteArray(Charsets.UTF_8))).trimEnd('=')
        }

        val encA1 = encryptUrl(plainA1)
        val encA2 = encryptUrl(plainA2)
        val encA3 = encryptUrl(plainA3)
        val encA4 = encryptUrl(plainA4)

        // XOR-obfuscate the password so it doesn't appear as a string literal in bytecode.
        val xorKey = ByteArray(32).also { rng.nextBytes(it) }
        fun xorStr(s: String) = s.toByteArray(Charsets.UTF_8)
            .mapIndexed { i, b -> (b.toInt() xor xorKey[i % 32].toInt()).toByte().toInt() }
            .joinToString(",")
        val keyStr = xorKey.map { it.toInt() }.joinToString(",")

        // The encrypted URL strings are safe to store as-is: without the password they're opaque.
        outputFile.writeText(buildString {
            appendLine("package xyz.thm.addon.utils;")
            appendLine()
            appendLine("public class password {")
            appendLine("    private static final byte[] K={$keyStr};")
            appendLine("    private static final byte[] PW={${xorStr(password)}};")
            appendLine("    private static final String A1=\"$encA1\";")
            appendLine("    private static final String A2=\"$encA2\";")
            appendLine("    private static final String A3=\"$encA3\";")
            appendLine("    private static final String A4=\"$encA4\";")
            appendLine()
            appendLine("    private static String d(byte[] v){byte[] r=new byte[v.length];int n=K.length;for(int i=0;i<v.length;i++)r[i]=(byte)(v[i]^K[i%n]);return new String(r,java.nio.charset.StandardCharsets.UTF_8);}")
            appendLine()
            appendLine("    public static String getPassword(){return d(PW);}")
            appendLine("    public static String getAPIMemberHud(){return A1;}")
            appendLine("    public static String getAPIHighway(){return A2;}")
            appendLine("    public static String getAPIStatus(){return A3;}")
            appendLine("    public static String getAPIHighwayStatus(){return A4;}")
            appendLine("}")
        }, Charsets.UTF_8)

        logger.lifecycle("Generated password.java — fresh random password, URLs encrypted, password XOR-obfuscated")
    }
}

tasks {
    processResources {
        val propertyMap = mapOf(
            "version" to project.version,
            "mc_version" to libs.versions.minecraft.get(),
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

        from("LICENSE") {
            rename { "${it}_${inputs.properties["archivesName"]}" }
        }
    }

    java {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 21
        options.isFork = true
        options.forkOptions.memoryMaximumSize = "3g"
options.compilerArgs.add("-Xlint:deprecation")
        options.compilerArgs.add("-Xlint:unchecked")
        dependsOn(generatePasswordClass)
    }
}
