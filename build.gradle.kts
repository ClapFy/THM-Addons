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

        val rng = SecureRandom()

        val password = ByteArray(32).also { rng.nextBytes(it) }.joinToString("") { "%02x".format(it) }

        fun encryptUrl(url: String): String {
            val key = SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(password.toByteArray(Charsets.UTF_8)), "AES")
            val c = Cipher.getInstance("AES"); c.init(Cipher.ENCRYPT_MODE, key)
            return Base64.getEncoder().encodeToString(c.doFinal(url.toByteArray(Charsets.UTF_8))).trimEnd('=')
        }

        val encA1 = encryptUrl(plainA1); val encA2 = encryptUrl(plainA2)
        val encA3 = encryptUrl(plainA3); val encA4 = encryptUrl(plainA4)

        // 32-byte master key split into nibbles.
        val rawKey = ByteArray(32).also { rng.nextBytes(it) }
        val kH = rawKey.map { ((it.toInt() ushr 4) and 0xF).toByte() }.toByteArray()
        val kL = rawKey.map { (it.toInt() and 0xF).toByte() }.toByteArray()

        // Random S-box permutation (fresh every build).
        val fsList = (0..255).toMutableList()
        for (i in 255 downTo 1) { val j = rng.nextInt(i + 1); val t = fsList[i]; fsList[i] = fsList[j]; fsList[j] = t }
        val fwdSbox = fsList.toIntArray()
        // invSbox[fwdSbox[i]] == i  =>  _v[_u[x]] == x  (double-lookup is identity)
        val invSbox = IntArray(256).also { for (i in 0..255) it[fwdSbox[i]] = i }

        // Several independent random decoy arrays — look identical to real key material.
        val da = ByteArray(32).also { rng.nextBytes(it) }
        val db = ByteArray(32).also { rng.nextBytes(it) }
        val dc = ByteArray(32).also { rng.nextBytes(it) }
        val dd = ByteArray(16).also { rng.nextBytes(it) }
        val dn = ByteArray(8).also  { rng.nextBytes(it) }
        val dx = ByteArray(16).also { rng.nextBytes(it) }

        val rot = rng.nextInt(6) + 1
        val stp = listOf(3, 7, 11, 13, 17, 19, 23, 29)[rng.nextInt(8)]

        fun rotL(b: Int, n: Int) = ((b shl n) and 0xFF) or (b ushr (8 - n))

        // Encode: fwdSbox -> XOR k0 -> rotL -> add k1 -> XOR k2
        fun enc(s: String) = s.toByteArray(Charsets.UTF_8).mapIndexed { i, byte ->
            val j  = (stp * i) % 32
            val k0 = rawKey[j].toInt()              and 0xFF
            val k1 = rawKey[(j + 7)  % 32].toInt() and 0xFF
            val k2 = rawKey[(j + 15) % 32].toInt() and 0xFF
            var b  = byte.toInt() and 0xFF
            b = fwdSbox[b]; b = b xor k0; b = rotL(b, rot); b = (b + k1) and 0xFF; b = b xor k2
            b.toByte().toInt()
        }.joinToString(",")

        fun ia(a: IntArray)  = a.joinToString(",")
        fun ba(b: ByteArray) = b.map { it.toInt() }.joinToString(",")

        // Pick a magic constant that looks like it has meaning (Knuth, FNV, etc.)
        // All values forced to signed Int so they emit as valid Java int literals.
        val magic = listOf(0x9e3779b9.toInt(), 0x517cc1b7.toInt(), 0x6c62272e, 0xdeadbeef.toInt(), 0xcafebabe.toInt())[rng.nextInt(5)]

        outputFile.writeText(buildString {
            appendLine("package xyz.thm.addon.utils;")
            appendLine("public class APIUtils {")

            // Key nibbles — neither half is the key alone.
            appendLine("    private static final byte[] _h={${ba(kH)}};")
            appendLine("    private static final byte[] _l={${ba(kL)}};")

            // S-boxes: _u = invSbox (real decoder), _v = fwdSbox (decoy decoder).
            // To an outside reader both are just 256-element int blobs.
            appendLine("    private static final int[]  _u={${ia(invSbox)}};")
            appendLine("    private static final int[]  _v={${ia(fwdSbox)}};")

            // Decoy arrays — random bytes, wired into every fake code path.
            appendLine("    private static final byte[] _a={${ba(da)}};")
            appendLine("    private static final byte[] _b={${ba(db)}};")
            appendLine("    private static final byte[] _c={${ba(dc)}};")
            appendLine("    private static final byte[] _d1={${ba(dd)}};")
            appendLine("    private static final byte[] _n={${ba(dn)}};")
            appendLine("    private static final byte[] _x={${ba(dx)}};")

            // Encoded payloads.
            appendLine("    private static final byte[] _p={${enc(password)}};")
            appendLine("    private static final byte[] _q={${enc(encA1)}};")
            appendLine("    private static final byte[] _r={${enc(encA2)}};")
            appendLine("    private static final byte[] _s={${enc(encA3)}};")
            appendLine("    private static final byte[] _t={${enc(encA4)}};")

            // Build-time constants baked in as plain int literals.
            appendLine("    private static final int _R=$rot;")
            appendLine("    private static final int _S=$stp;")
            appendLine("    private static final int _M=$magic;")

            // ── Real helpers ──────────────────────────────────────────────────────────────
            appendLine("    private static byte[] _k(){byte[] k=new byte[32];for(int i=0;i<32;i++)k[i]=(byte)((_h[i]<<4)|(_l[i]&0xF));return k;}")
            appendLine("    private static int _rr(int b,int n){return((b>>>n)|((b<<(8-n))&0xFF));}")

            // ── Fake helpers — all produce noise or silently cancel out ───────────────────
            // Rotate-left: used only in fake paths so the real _rr is not the only rotation.
            appendLine("    private static int _rl(int b,int n){return((b<<n)&0xFF)|(b>>>(8-n));}")
            // Multiplicative mixing step — feeds _sc and _ks only.
            appendLine("    private static int _mix(int a,int b){return(((a*_M)>>>24)^(b>>>3))&0xFF;}")
            // Key-dependent XOR with decoy _a — looks like key-stream selection.
            appendLine("    private static int _mux(int x,int i){return x^(_a[i%_a.length]&0xFF);}")
            // Double S-box lookup: _v[_u[x]] = fwdSbox[invSbox[x]] = x (identity).
            // Looks like a substitution; actually does nothing net.
            appendLine("    private static int _tab(int x){return _v[_u[x&0xFF]&0xFF];}")
            // Wrong key: _k() XOR'd with decoy _b — deliberately broken, used in _prep only.
            appendLine("    private static byte[] _kx(){byte[]k=_k();byte[]r=new byte[32];for(int i=0;i<32;i++)r[i]=(byte)(k[i]^(_b[i]&0xFF));return r;}")
            // Step function calling _mix and _mux — feeds fake paths only.
            appendLine("    private static int _sc(int b,byte[]k,int i){int j=(_S*i)%32;return _mix(b^(k[j]&0xFF),k[(j+7)%32]&0xFF)^_mux(b,i);}")
            // FNV-1a mixed with _x — used in dead-branch guards.
            appendLine("    private static int _chk(byte[]v){int h=0x811c9dc5;for(int i=0;i<v.length;i++)h=(h^((v[i]&0xFF)^(_x[i%16]&0xFF)))*0x01000193;return h;}")
            // 'Integrity check' over _v and _a — result capped at 16 bits, so == _M always false.
            appendLine("    private static int _ic(byte[]v){int s=0;for(int i=0;i<v.length;i++)s+=_v[v[i]&0xFF]^(_a[i%_a.length]&0xFF);return s&0xFFFF;}")
            // Fake key schedule — int[] from _sc and _c, consumed only by _sel dead branches.
            appendLine("    private static int[]_ks(){int[]s=new int[8];byte[]k=_k();for(int i=0;i<8;i++)s[i]=_sc(k[i*4]&0xFF,k,i)^(_c[i%_c.length]&0xFF);return s;}")
            // Second fake key derivation — applies _mix and _n on top of the already-wrong _kx.
            appendLine("    private static byte[]_kd(){byte[]k=_kx();byte[]r=new byte[32];for(int i=0;i<32;i++)r[i]=(byte)(_mix(k[i]&0xFF,_d1[i%_d1.length]&0xFF)^(_n[i%_n.length]&0xFF));return r;}")

            // ── Fake decode (_prep) ───────────────────────────────────────────────────────
            // Uses _kd (doubly-wrong key) + _sc (wrong step) + _tab (identity) + _rl (wrong dir).
            // Produces garbage. Called only from dead branches in _sel.
            appendLine("    private static byte[]_prep(byte[]v){byte[]k=_kd();byte[]o=new byte[v.length];for(int i=0;i<v.length;i++){int b=_sc(v[i]&0xFF,k,i);b=_rl(_tab(b),_R);o[i]=(byte)b;}return o;}")

            // ── Real decode (_z) ──────────────────────────────────────────────────────────
            // Exact inverse of enc(): XOR k2 -> sub k1 -> rotR -> XOR k0 -> invSbox (_u).
            appendLine("    private static byte[]_z(byte[]v){byte[]k=_k();byte[]o=new byte[v.length];for(int i=0;i<v.length;i++){int j=(_S*i)%32;int k0=k[j]&0xFF,k1=k[(j+7)%32]&0xFF,k2=k[(j+15)%32]&0xFF;int b=v[i]&0xFF;b^=k2;b=(b-k1+256)&0xFF;b=_rr(b,_R);b^=k0;b=_u[b];o[i]=(byte)b;}return o;}")

            // ── Post-processing stub (_chkOut) ────────────────────────────────────────────
            // Looks like it would scramble output on integrity failure.
            // Guard: (XOR-of-bytes & 0xFF000000) == _M  ->  XOR of bytes is 0..255
            // so upper 3 bytes are always 0, making this ALWAYS false.
            appendLine("    private static void _chkOut(byte[]r){int s=0;for(byte b:r)s^=(b&0xFF);if((s&0xFF000000)==_M)for(int i=0;i<r.length;i++)r[i]^=_n[i%_n.length];}")

            // ── Output wrapper (_out) ─────────────────────────────────────────────────────
            // Calls real _z, then the always-no-op _chkOut, returns the string.
            appendLine("    private static String _out(byte[]v){byte[]r=_z(v);_chkOut(r);return new java.lang.String(r,java.nio.charset.StandardCharsets.UTF_8);}")

            // ── Selector (_sel) ───────────────────────────────────────────────────────────
            // Three guards, all permanently false — always falls through to _out.
            //   Guard 1: (c & MAX_VALUE) < 0  — clearing the sign bit makes c >= 0 always.
            //   Guard 2: _ic(v) == _M          — _ic returns 0..65535, _M is a 32-bit constant > 65535.
            //   Guard 3: _chk(v) == 0           — FNV of real data is never 0.
            // Any static analysis of the branches incorrectly implies _prep might run.
            appendLine("    private static String _sel(byte[]v){int[]ks=_ks();int c=_chk(v)^ks[0];int ic=_ic(v);if((c&Integer.MAX_VALUE)<0)return new java.lang.String(_prep(v),java.nio.charset.StandardCharsets.UTF_8);if(ic==_M)return new java.lang.String(_prep(v),java.nio.charset.StandardCharsets.UTF_8);if(_chk(v)==0)return new java.lang.String(_prep(v),java.nio.charset.StandardCharsets.UTF_8);return _out(v);}")

            appendLine("    public static String getPassword(){return _sel(_p);}")
            appendLine("    private static String getAPIMemberHud(){return _sel(_q);}")
            appendLine("    private static String getAPIHighway(){return _sel(_r);}")
            appendLine("    private static String getAPIStatus(){return _sel(_s);}")
            appendLine("    private static String getAPIHighwayStatus(){return _sel(_t);}")
            appendLine("""    private static String _dA(String e,String p){try{java.security.MessageDigest d=java.security.MessageDigest.getInstance("SHA-256");byte[]k=d.digest(p.getBytes(java.nio.charset.StandardCharsets.UTF_8));String s=e;int r=s.length()%4;if(r>0)s+="=".repeat(4-r);byte[]b=java.util.Base64.getDecoder().decode(s);javax.crypto.spec.SecretKeySpec sk=new javax.crypto.spec.SecretKeySpec(k,0,k.length,"AES");javax.crypto.Cipher c=javax.crypto.Cipher.getInstance("AES");c.init(javax.crypto.Cipher.DECRYPT_MODE,sk);byte[]o=c.doFinal(b);java.lang.String res=new java.lang.String(o,java.nio.charset.StandardCharsets.UTF_8);if(!res.startsWith("http://")&&!res.startsWith("https://"))return null;return res;}catch(Exception ex){return null;}}""")
            appendLine("""    private static String _fG(String url){try{java.net.HttpURLConnection cn=(java.net.HttpURLConnection)new java.net.URI(url).toURL().openConnection();cn.setRequestMethod("GET");cn.setConnectTimeout(5000);cn.setReadTimeout(5000);if(cn.getResponseCode()!=200){cn.disconnect();return null;}StringBuilder sb=new StringBuilder();try(java.util.Scanner sc=new java.util.Scanner(cn.getInputStream())){while(sc.hasNextLine())sb.append(sc.nextLine());}cn.disconnect();return sb.toString();}catch(Exception e){return null;}}""")
            appendLine("""    private static void _sA(String msg,String pw,String enc,String lt){new Thread(()->{java.net.HttpURLConnection cn=null;try{java.lang.String url=_dA(enc,pw);if(url==null)return;cn=(java.net.HttpURLConnection)new java.net.URI(url).toURL().openConnection();cn.setRequestMethod("POST");cn.setRequestProperty("Content-Type","application/json");cn.setDoOutput(true);cn.setConnectTimeout(10000);cn.setReadTimeout(10000);java.lang.String json="{\"content\": \""+msg.replace("\"","\\\"")+"\"}" ;try(java.io.OutputStream os=cn.getOutputStream()){byte[]b=json.getBytes(java.nio.charset.StandardCharsets.UTF_8);os.write(b,0,b.length);os.flush();}int rc=cn.getResponseCode();try(java.io.InputStream is=rc>=400?cn.getErrorStream():cn.getInputStream()){if(is!=null)is.readAllBytes();}if(rc!=204&&rc!=200)xyz.thm.addon.THMAddon.LOG.warn("API response code: "+rc);}catch(Exception e){xyz.thm.addon.THMAddon.LOG.warn("Failed to send "+lt+" to API");}finally{if(cn!=null)cn.disconnect();}}).start();}""")
            appendLine("""    private static void _sW(String url,String msg){new Thread(()->{try{java.net.HttpURLConnection cn=(java.net.HttpURLConnection)new java.net.URI(url).toURL().openConnection();cn.setRequestMethod("POST");cn.setRequestProperty("Content-Type","application/json");cn.setDoOutput(true);java.lang.String json="{\"content\": \""+msg.replace("\"","\\\"")+"\"}" ;try(java.io.OutputStream os=cn.getOutputStream()){byte[]b=json.getBytes(java.nio.charset.StandardCharsets.UTF_8);os.write(b,0,b.length);}int rc=cn.getResponseCode();if(rc!=204&&rc!=200)xyz.thm.addon.THMAddon.LOG.warn("Webhook response code: "+rc);cn.disconnect();}catch(Exception e){xyz.thm.addon.THMAddon.LOG.warn("Failed to send to webhook: "+e.getMessage());}}).start();}""")
            appendLine("    private static String fetchApiResponse(String encryptedUrl){java.lang.String url=_dA(encryptedUrl,getPassword());if(url==null)return null;return _fG(url);}")
            appendLine("    public static void sendStatus(String message){_sA(message,getPassword(),getAPIStatus(),\"status\");}")
            appendLine("    public static void sendStatistics(String message){_sA(message,getPassword(),getAPIHighway(),\"statistics\");}")
            appendLine("    public static void sendToWebhook(String url,String message){_sW(url,message);}")
            appendLine("""    public static java.util.List<ThmMembers.Member> fetchMembersFromApi(){try{String response=fetchApiResponse(getAPIMemberHud());if(response==null)return null;xyz.thm.addon.THMAddon.LOG.info("Fetched Members");com.google.gson.Gson gson=new com.google.gson.Gson();com.google.gson.JsonArray jsonArray=gson.fromJson(response,com.google.gson.JsonArray.class);java.util.List<ThmMembers.Member> members=new java.util.ArrayList<>();for(int i=0;i<jsonArray.size();i++){com.google.gson.JsonObject jsonObject=jsonArray.get(i).getAsJsonObject();com.google.gson.JsonArray usernamesArray=jsonObject.getAsJsonArray("usernames");String[]usernames=new String[usernamesArray.size()];for(int j=0;j<usernamesArray.size();j++)usernames[j]=usernamesArray.get(j).getAsString();String rank=jsonObject.get("rank").getAsString();String rankId=jsonObject.has("rankId")?jsonObject.getAsJsonPrimitive("rankId").getAsString():"";String branch=jsonObject.has("branch")?jsonObject.getAsJsonPrimitive("branch").getAsString():"";String displayName=usernames.length>0?usernames[0]:"Unknown";members.add(new ThmMembers.Member(displayName,usernames,rank,rankId,branch));}return members;}catch(Exception e){xyz.thm.addon.THMAddon.LOG.warn("Error fetching members from API: {}",e.getMessage());}return null;}""")
            appendLine("""    public static java.util.Map<String,String> fetchHighwayStatusFromApi(){try{String body=fetchApiResponse(getAPIHighwayStatus());if(body==null)return null;com.google.gson.Gson gson=new com.google.gson.Gson();com.google.gson.JsonObject root=gson.fromJson(body,com.google.gson.JsonObject.class);java.util.Map<String,String> highwayByName=new java.util.HashMap<>();java.util.Map<String,Long> newestTimestampByName=new java.util.HashMap<>();if(root!=null){for(java.util.Map.Entry<String,com.google.gson.JsonElement> entry:root.entrySet()){String highway=entry.getKey();com.google.gson.JsonObject value=entry.getValue().getAsJsonObject();if(value==null||!value.has("username"))continue;String raw=value.get("username").getAsString().trim().toLowerCase(java.util.Locale.ROOT);if(raw.isEmpty()||"unknown".equals(raw))continue;long timestamp=value.has("timestamp")?value.get("timestamp").getAsLong():Long.MIN_VALUE;Long existingTimestamp=newestTimestampByName.get(raw);if(existingTimestamp==null||timestamp>=existingTimestamp){newestTimestampByName.put(raw,timestamp);highwayByName.put(raw,highway);}}}return highwayByName;}catch(Exception e){xyz.thm.addon.THMAddon.LOG.warn("Error fetching highway status from API: {}",e.getMessage());return null;}}""")
            appendLine("}")
        }, Charsets.UTF_8)

        logger.lifecycle("Generated APIUtils.java (M=${magic.toLong().and(0xFFFFFFFFL).toString(16)} rot=$rot step=$stp)")
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
        dependsOn(generateAPIUtils)
    }
}
