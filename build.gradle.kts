import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Properties
import java.util.Random
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

val generateAPIUtils by tasks.registering {
    val secretsFile = file("secrets.properties")
    val outputFile  = file("src/main/java/xyz/thm/addon/utils/APIUtils.java")

    inputs.file(secretsFile)
    outputs.file(outputFile)
    outputs.upToDateWhen { false }

    doLast {
        file("src/main/java/xyz/thm/addon/utils/password.java").delete()
        if (!secretsFile.exists()) error(
            "secrets.properties not found. Copy secrets.properties.example, fill in your API URLs, then rebuild."
        )

        val props = Properties()
        secretsFile.bufferedReader(Charsets.UTF_8).use<java.io.Reader, Unit> { props.load(it) }

        fun req(k: String) = props.getProperty(k) ?: error("secrets.properties missing key: $k")

        val plainA1 = req("api.memberHud"); val plainA2 = req("api.highway")
        val plainA3 = req("api.status");    val plainA4 = req("api.highwayStatus")

        // ── Per-build unique name pool ────────────────────────────────────────────────────
        // Every build generates different identifier names; no two builds share a signature.
        val usedNames = mutableSetOf<String>()
        val javaReserved = setOf("abstract","assert","boolean","break","byte","case","catch",
            "char","class","const","continue","default","do","double","else","enum","extends",
            "final","finally","float","for","goto","if","implements","import","instanceof",
            "int","interface","long","native","new","package","private","protected","public",
            "return","short","static","strictfp","super","switch","synchronized","this","throw",
            "throws","transient","try","void","volatile","while","true","false","null",
            "length","wait","notify","clone","equals","hashCode","toString","getClass","main")

        val rng = SecureRandom()

        fun rn(): String {
            val c = "bcdfghjklmnpqrstvwxz"; val v = "aeiou"
            while (true) {
                val sb = StringBuilder("_")
                repeat(rng.nextInt(3) + 2) { sb.append(c[rng.nextInt(c.length)]); if (rng.nextBoolean()) sb.append(v[rng.nextInt(v.length)]) }
                val n = sb.toString()
                if (n.length >= 3 && n !in usedNames && n !in javaReserved) { usedNames += n; return n }
            }
        }

        // ── Password + URL encryption ─────────────────────────────────────────────────────
        val password = ByteArray(32).also { rng.nextBytes(it) }.joinToString("") { "%02x".format(it) }
        val masterKey: ByteArray = MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8))

        fun encryptUrl(url: String): String {
            val c = Cipher.getInstance("AES"); c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(masterKey, "AES"))
            return Base64.getEncoder().encodeToString(c.doFinal(url.toByteArray(Charsets.UTF_8))).trimEnd('=')
        }

        val encA1 = encryptUrl(plainA1); val encA2 = encryptUrl(plainA2)
        val encA3 = encryptUrl(plainA3); val encA4 = encryptUrl(plainA4)

        // ── Encoding scheme ───────────────────────────────────────────────────────────────
        // Encode per byte: XOR(k0) → ROTL(rot) → ADD(k1) → XOR(k2)
        // Decode per byte: XOR(k2) → SUB(k1)   → ROTR(rot) → XOR(k0)
        val stp   = listOf(3, 7, 11, 13, 17, 19, 23, 29)[rng.nextInt(8)]
        val rot   = rng.nextInt(6) + 1
        val k0off = listOf(1, 3, 5)[rng.nextInt(3)]           // non-zero so offsets don't trivially order
        val k1off = listOf(7, 9, 11, 13)[rng.nextInt(4)]
        val k2off = listOf(17, 19, 21, 23)[rng.nextInt(4)]

        fun rotl8(b: Int, n: Int) = ((b shl n) and 0xFF) or (b ushr (8 - n))

        fun encodePayload(s: String): IntArray = s.toByteArray(Charsets.UTF_8).mapIndexed { i, byte ->
            val j  = (stp * i) % 32
            val e0 = masterKey[(j + k0off) % 32].toInt() and 0xFF
            val e1 = masterKey[(j + k1off) % 32].toInt() and 0xFF
            val e2 = masterKey[(j + k2off) % 32].toInt() and 0xFF
            var b  = byte.toInt() and 0xFF
            b = b xor e0; b = rotl8(b, rot); b = (b + e1) and 0xFF; b = b xor e2
            b
        }.toIntArray()

        // ── 3-way key split ───────────────────────────────────────────────────────────────
        // masterKey = kA XOR kB XOR kC; each share stored with independent obfuscation.
        val kA = ByteArray(32).also { rng.nextBytes(it) }
        val kB = ByteArray(32).also { rng.nextBytes(it) }
        val kC = ByteArray(32) { (masterKey[it].toInt() xor kA[it].toInt() xor kB[it].toInt()).toByte() }

        // Share A: XOR with 32-byte random decoy dA.
        val dA  = ByteArray(32).also { rng.nextBytes(it) }
        val sKA = ByteArray(32) { ((kA[it].toInt() and 0xFF) xor (dA[it].toInt() and 0xFF)).toByte() }

        // Share B: rotated left by rotB, then XOR with 16-byte decoy dB.
        val rotB = rng.nextInt(7) + 1
        val dB   = ByteArray(16).also { rng.nextBytes(it) }
        val sKB  = ByteArray(32) { rotl8(kB[it].toInt() and 0xFF, rotB).let { r -> (r xor (dB[it % dB.size].toInt() and 0xFF)).toByte() } }

        // Share C: XOR with index-scaled constant and 16-byte decoy dC.
        val mulC = listOf(3, 5, 7, 11, 13, 17, 19, 23)[rng.nextInt(8)]
        val dC   = ByteArray(16).also { rng.nextBytes(it) }
        val sKC  = ByteArray(32) { ((kC[it].toInt() and 0xFF) xor ((it * mulC) and 0xFF) xor (dC[it % dC.size].toInt() and 0xFF)).toByte() }

        // ── Payload fragmentation ─────────────────────────────────────────────────────────
        // Each payload split into two XOR-parts; neither fragment alone is the encoded payload.
        val payloads = listOf(password, encA1, encA2, encA3, encA4).map { encodePayload(it) }
        data class Frag(val f0: IntArray, val f1: IntArray)
        val splits = payloads.map { enc ->
            val mask = IntArray(enc.size) { rng.nextInt(256) }
            Frag(IntArray(enc.size) { enc[it] xor mask[it] }, mask)
        }

        // ── Decode path selection ─────────────────────────────────────────────────────────
        // realPath = (masterKey[pi1] ^ masterKey[pi2] ^ masterKey[pi3]) & 3.
        // Statically unknown — requires knowing masterKey, which requires key reconstruction.
        val pi1 = rng.nextInt(32); val pi2 = rng.nextInt(32); val pi3 = rng.nextInt(32)
        val realPath = ((masterKey[pi1].toInt() and 0xFF) xor
                        (masterKey[pi2].toInt() and 0xFF) xor
                        (masterKey[pi3].toInt() and 0xFF)) and 3

        // 4 structurally identical decoders: real path uses correct offsets, fakes differ by 1.
        data class DP(val o0: Int, val o1: Int, val o2: Int)
        val dp = Array(4) { i ->
            when {
                i == realPath               -> DP(k0off, k1off, k2off)
                i == ((realPath + 1) and 3) -> DP(k0off, (k1off + 1) % 32, k2off)
                i == ((realPath + 2) and 3) -> DP((k0off + 1) % 32, k1off, k2off)
                else                        -> DP(k0off, k1off, (k2off + 1) % 32)
            }
        }

        // Branch check order randomised so no single path visually dominates.
        val ord = (0..3).toMutableList().also { lst ->
            for (i in 3 downTo 1) { val j = rng.nextInt(i + 1); val t = lst[i]; lst[i] = lst[j]; lst[j] = t }
        }

        // ── Name assignments ──────────────────────────────────────────────────────────────
        val NA = rn(); val NB = rn(); val NC = rn()
        val NDA = rn(); val NDB = rn(); val NDC = rn()
        val PF  = Array(5) { Pair(rn(), rn()) }          // (frag0, frag1) per payload
        val DEC = Array(8) { rn() }                       // pure decoy field names
        val decoyData = Array(8) { ByteArray(if (it % 3 == 0) 32 else if (it % 3 == 1) 16 else 8).also { a -> rng.nextBytes(a) } }

        val MRK  = rn(); val MPI = rn(); val MRF = rn()  // key/path/fragment helpers
        val MD   = Array(4) { rn() }                      // 4 decode variants
        val MSEL = rn()                                    // dispatcher
        val MDA  = rn(); val MFG = rn()                   // HTTP helpers
        val MSA  = rn(); val MSW = rn(); val MFAR = rn()  // post/webhook/fetch helpers

        // ── Byte serialisers ──────────────────────────────────────────────────────────────
        fun ba(b: ByteArray) = b.joinToString(",") { it.toInt().toString() }
        // Payload values are 0-255; Java byte literals are -128..127.
        fun ia(a: IntArray)  = a.joinToString(",") { if (it > 127) (it - 256).toString() else it.toString() }

        // ── Build field declarations (shuffled for additional noise) ───────────────────────
        val fields = mutableListOf<String>()
        fields += "    private static final byte[] $NA={${ba(sKA)}};"
        fields += "    private static final byte[] $NB={${ba(sKB)}};"
        fields += "    private static final byte[] $NC={${ba(sKC)}};"
        fields += "    private static final byte[] $NDA={${ba(dA)}};"
        fields += "    private static final byte[] $NDB={${ba(dB)}};"
        fields += "    private static final byte[] $NDC={${ba(dC)}};"
        DEC.indices.forEach { fields += "    private static final byte[] ${DEC[it]}={${ba(decoyData[it])}};" }
        for (idx in 0..4) {
            fields += "    private static final byte[] ${PF[idx].first}={${ia(splits[idx].f0)}};"
            fields += "    private static final byte[] ${PF[idx].second}={${ia(splits[idx].f1)}};"
        }
        fields.shuffle(Random(rng.nextLong()))

        // ── Method bodies (all constants inlined — no named int fields in generated class) ─
        // Key reconstruction: a = kA, b = rotr(sKB ^ dB) = kB, c = sKC ^ idx*mul ^ dC = kC
        val rkBody = "byte[]r=new byte[32];" +
            "for(int i=0;i<32;i++){" +
            "int a=($NA[i]&0xFF)^($NDA[i]&0xFF);" +
            "int b=($NB[i]&0xFF)^($NDB[i%${dB.size}]&0xFF);" +
            "b=((b>>>$rotB)|((b<<${8-rotB})&0xFF));" +
            "int c=($NC[i]&0xFF)^((i*$mulC)&0xFF)^($NDC[i%${dC.size}]&0xFF);" +
            "r[i]=(byte)(a^b^c);}return r;"

        // Decode body builder: only realPath offsets (o0,o1,o2) reverse the encoding correctly.
        fun decBody(p: DP) = "byte[]r=new byte[v.length];" +
            "for(int i=0;i<v.length;i++){" +
            "int j=($stp*i)%32;" +
            "int t0=(k[(j+${p.o0})%32]&0xFF);" +
            "int t1=(k[(j+${p.o1})%32]&0xFF);" +
            "int t2=(k[(j+${p.o2})%32]&0xFF);" +
            "int b=(v[i]&0xFF)^t2;" +
            "b=(b-t1+256)&0xFF;" +
            "b=((b>>>$rot)|((b<<${8-rot})&0xFF));" +
            "r[i]=(byte)(b^t0);}" +
            "return new java.lang.String(r,java.nio.charset.StandardCharsets.UTF_8);"

        // Selector body: data-dependent dispatch — no permanently-false guards anywhere.
        val selBody = buildString {
            append("byte[]k=$MRK();byte[]v=$MRF(f0,f1);int p=$MPI(k);")
            for (ci in 0..2) append("if(p==${ord[ci]})return ${MD[ord[ci]]}(k,v);")
            append("return ${MD[ord[3]]}(k,v);")
        }

        outputFile.writeText(buildString {
            appendLine("package xyz.thm.addon.utils;")
            appendLine("public class APIUtils {")
            fields.forEach { appendLine(it) }
            appendLine("    private static byte[] $MRK(){$rkBody}")
            appendLine("    private static int $MPI(byte[]k){return((k[$pi1]&0xFF)^(k[$pi2]&0xFF)^(k[$pi3]&0xFF))&3;}")
            appendLine("    private static byte[] $MRF(byte[]f0,byte[]f1){byte[]r=new byte[f0.length];for(int i=0;i<f0.length;i++)r[i]=(byte)((f0[i]&0xFF)^(f1[i]&0xFF));return r;}")
            for (di in 0..3) appendLine("    private static String ${MD[di]}(byte[]k,byte[]v){${decBody(dp[di])}}")
            appendLine("    private static String $MSEL(byte[]f0,byte[]f1){$selBody}")
            appendLine("    public static String getPassword(){return $MSEL(${PF[0].first},${PF[0].second});}")
            appendLine("    private static String getAPIMemberHud(){return $MSEL(${PF[1].first},${PF[1].second});}")
            appendLine("    private static String getAPIHighway(){return $MSEL(${PF[2].first},${PF[2].second});}")
            appendLine("    private static String getAPIStatus(){return $MSEL(${PF[3].first},${PF[3].second});}")
            appendLine("    private static String getAPIHighwayStatus(){return $MSEL(${PF[4].first},${PF[4].second});}")
            appendLine("""    private static String $MDA(String e,String p){try{java.security.MessageDigest d=java.security.MessageDigest.getInstance("SHA-256");byte[]k=d.digest(p.getBytes(java.nio.charset.StandardCharsets.UTF_8));String s=e;int r=s.length()%4;if(r>0)s+="=".repeat(4-r);byte[]b=java.util.Base64.getDecoder().decode(s);javax.crypto.spec.SecretKeySpec sk=new javax.crypto.spec.SecretKeySpec(k,0,k.length,"AES");javax.crypto.Cipher c=javax.crypto.Cipher.getInstance("AES");c.init(javax.crypto.Cipher.DECRYPT_MODE,sk);byte[]o=c.doFinal(b);java.lang.String res=new java.lang.String(o,java.nio.charset.StandardCharsets.UTF_8);if(!res.startsWith("http://")&&!res.startsWith("https://"))return null;return res;}catch(Exception ex){return null;}}""")
            appendLine("""    private static String $MFG(String url){try{java.net.HttpURLConnection cn=(java.net.HttpURLConnection)new java.net.URI(url).toURL().openConnection();cn.setRequestMethod("GET");cn.setConnectTimeout(5000);cn.setReadTimeout(5000);if(cn.getResponseCode()!=200){cn.disconnect();return null;}StringBuilder sb=new StringBuilder();try(java.util.Scanner sc=new java.util.Scanner(cn.getInputStream())){while(sc.hasNextLine())sb.append(sc.nextLine());}cn.disconnect();return sb.toString();}catch(Exception e){return null;}}""")
            appendLine("""    private static void $MSA(String msg,String pw,String enc,String lt){new Thread(()->{java.net.HttpURLConnection cn=null;try{java.lang.String url=$MDA(enc,pw);if(url==null)return;cn=(java.net.HttpURLConnection)new java.net.URI(url).toURL().openConnection();cn.setRequestMethod("POST");cn.setRequestProperty("Content-Type","application/json");cn.setDoOutput(true);cn.setConnectTimeout(10000);cn.setReadTimeout(10000);java.lang.String json="{\"content\": \""+msg.replace("\"","\\\"")+"\"}" ;try(java.io.OutputStream os=cn.getOutputStream()){byte[]b=json.getBytes(java.nio.charset.StandardCharsets.UTF_8);os.write(b,0,b.length);os.flush();}int rc=cn.getResponseCode();try(java.io.InputStream is=rc>=400?cn.getErrorStream():cn.getInputStream()){if(is!=null)is.readAllBytes();}if(rc!=204&&rc!=200)xyz.thm.addon.THMAddon.LOG.warn("API response code: "+rc);}catch(Exception e){xyz.thm.addon.THMAddon.LOG.warn("Failed to send "+lt+" to API");}finally{if(cn!=null)cn.disconnect();}}).start();}""")
            appendLine("""    private static void $MSW(String url,String msg){new Thread(()->{try{java.net.HttpURLConnection cn=(java.net.HttpURLConnection)new java.net.URI(url).toURL().openConnection();cn.setRequestMethod("POST");cn.setRequestProperty("Content-Type","application/json");cn.setDoOutput(true);java.lang.String json="{\"content\": \""+msg.replace("\"","\\\"")+"\"}" ;try(java.io.OutputStream os=cn.getOutputStream()){byte[]b=json.getBytes(java.nio.charset.StandardCharsets.UTF_8);os.write(b,0,b.length);}int rc=cn.getResponseCode();if(rc!=204&&rc!=200)xyz.thm.addon.THMAddon.LOG.warn("Webhook response code: "+rc);cn.disconnect();}catch(Exception e){xyz.thm.addon.THMAddon.LOG.warn("Failed to send to webhook: "+e.getMessage());}}).start();}""")
            appendLine("    private static String $MFAR(String eu){java.lang.String url=$MDA(eu,getPassword());if(url==null)return null;return $MFG(url);}")
            appendLine("    public static void sendStatus(String message){$MSA(message,getPassword(),getAPIStatus(),\"status\");}")
            appendLine("    public static void sendStatistics(String message){$MSA(message,getPassword(),getAPIHighway(),\"statistics\");}")
            appendLine("    public static void sendToWebhook(String url,String message){$MSW(url,message);}")
            appendLine("""    public static java.util.List<ThmMembers.Member> fetchMembersFromApi(){try{String response=$MFAR(getAPIMemberHud());if(response==null)return null;xyz.thm.addon.THMAddon.LOG.info("Fetched Members");com.google.gson.Gson gson=new com.google.gson.Gson();com.google.gson.JsonArray jsonArray=gson.fromJson(response,com.google.gson.JsonArray.class);java.util.List<ThmMembers.Member> members=new java.util.ArrayList<>();for(int i=0;i<jsonArray.size();i++){com.google.gson.JsonObject jsonObject=jsonArray.get(i).getAsJsonObject();com.google.gson.JsonArray usernamesArray=jsonObject.getAsJsonArray("usernames");String[]usernames=new String[usernamesArray.size()];for(int j=0;j<usernamesArray.size();j++)usernames[j]=usernamesArray.get(j).getAsString();String rank=jsonObject.get("rank").getAsString();String rankId=jsonObject.has("rankId")?jsonObject.getAsJsonPrimitive("rankId").getAsString():"";String branch=jsonObject.has("branch")?jsonObject.getAsJsonPrimitive("branch").getAsString():"";String displayName=usernames.length>0?usernames[0]:"Unknown";members.add(new ThmMembers.Member(displayName,usernames,rank,rankId,branch));}return members;}catch(Exception e){xyz.thm.addon.THMAddon.LOG.warn("Error fetching members from API: {}",e.getMessage());}return null;}""")
            appendLine("""    public static java.util.Map<String,String> fetchHighwayStatusFromApi(){try{String body=$MFAR(getAPIHighwayStatus());if(body==null)return null;com.google.gson.Gson gson=new com.google.gson.Gson();com.google.gson.JsonObject root=gson.fromJson(body,com.google.gson.JsonObject.class);java.util.Map<String,String> highwayByName=new java.util.HashMap<>();java.util.Map<String,Long> newestTimestampByName=new java.util.HashMap<>();if(root!=null){for(java.util.Map.Entry<String,com.google.gson.JsonElement> entry:root.entrySet()){String highway=entry.getKey();com.google.gson.JsonObject value=entry.getValue().getAsJsonObject();if(value==null||!value.has("username"))continue;String raw=value.get("username").getAsString().trim().toLowerCase(java.util.Locale.ROOT);if(raw.isEmpty()||"unknown".equals(raw))continue;long timestamp=value.has("timestamp")?value.get("timestamp").getAsLong():Long.MIN_VALUE;Long existingTimestamp=newestTimestampByName.get(raw);if(existingTimestamp==null||timestamp>=existingTimestamp){newestTimestampByName.put(raw,timestamp);highwayByName.put(raw,highway);}}}return highwayByName;}catch(Exception e){xyz.thm.addon.THMAddon.LOG.warn("Error fetching highway status from API: {}",e.getMessage());return null;}}""")
            appendLine("}")
        }, Charsets.UTF_8)

        logger.lifecycle("Generated APIUtils.java (stp=$stp rot=$rot k0=$k0off k1=$k1off k2=$k2off rotB=$rotB mulC=$mulC rpath=$realPath)")
    }
}

sourceSets {
    main {
        java {
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
        exclude("xyz/thm/addon/modules/HandshakeHostTest.class")
        exclude("xyz/thm/addon/modules/HandshakeHostTest$*.class")
        exclude("xyz/thm/addon/mixin/ClientConnectionMixin.class")
        exclude("xyz/thm/addon/mixin/HandshakeC2SPacketMixin.class")
        exclude("xyz/thm/addon/mixin/MultiplayerScreenRouteButtonMixin.class")
        exclude("xyz/thm/addon/mixin/TitleScreenFastestRouteButtonMixin.class")

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
        dependsOn(generateAPIUtils)
    }
}
