/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

/*
 * Adapted from Meteor Client's PacketLogger module.
 * Original source: https://github.com/MeteorDevelopment/meteor-client
 * Copyright (c) Meteor Development.
 *
 * THM-specific changes:
 * - Structured JSONL output for replay-grade packet logging
 * - Exact field extraction for common packet types
 * - File-first defaults with THM-owned log directory
 */

package xyz.thm.addon.modules;

import com.google.gson.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.network.PacketUtils;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.HashedStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import xyz.thm.addon.THMAddon;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public class PacketLoggerTHM extends Module {
    private static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new GsonBuilder()
        .serializeNulls()
        .disableHtmlEscaping()
        .create();
    private static final Path PACKET_LOGS_DIR = FabricLoader.getInstance().getGameDir().resolve("logs").resolve("thm-packet-logs");
    private static final DateTimeFormatter FILE_NAME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    private static final DateTimeFormatter WALL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withZone(ZoneOffset.UTC);
    private static final int LINE_SEPARATOR_BYTES = System.lineSeparator().getBytes(StandardCharsets.UTF_8).length;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgOutput = settings.createGroup("Output");

    private final Setting<Set<PacketType<? extends Packet<?>>>> s2cPackets = sgGeneral.add(new PacketListSetting.Builder()
        .name("S2C-packets")
        .description("Server-to-client packets to log.")
        .filter(type -> PacketUtils.getClientboundPackets().contains(type))
        .defaultValue(new ObjectOpenHashSet<>(PacketUtils.getClientboundPackets()))
        .build()
    );

    private final Setting<Set<PacketType<? extends Packet<?>>>> c2sPackets = sgGeneral.add(new PacketListSetting.Builder()
        .name("C2S-packets")
        .description("Client-to-server packets to log.")
        .filter(type -> PacketUtils.getServerboundPackets().contains(type))
        .defaultValue(new ObjectOpenHashSet<>(PacketUtils.getServerboundPackets()))
        .build()
    );

    private final Setting<Boolean> logToFile = sgOutput.add(new BoolSetting.Builder()
        .name("log-to-file")
        .description("Write packet logs to structured JSONL files.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> logToChat = sgOutput.add(new BoolSetting.Builder()
        .name("log-to-chat")
        .description("Preview logged packets in chat.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> showTimestamp = sgOutput.add(new BoolSetting.Builder()
        .name("show-timestamp")
        .description("Show timestamps in chat previews.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showCount = sgOutput.add(new BoolSetting.Builder()
        .name("show-count")
        .description("Show per-packet counts in chat previews.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> showSummary = sgOutput.add(new BoolSetting.Builder()
        .name("show-summary")
        .description("Show a packet count summary in chat when the module deactivates.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> captureRawToString = sgOutput.add(new BoolSetting.Builder()
        .name("capture-raw-to-string")
        .description("Include the packet's raw toString() output in JSONL records.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> flushInterval = sgOutput.add(new IntSetting.Builder()
        .name("flush-interval")
        .description("How often to flush packet logs to disk in seconds.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 10)
        .visible(logToFile::get)
        .build()
    );

    private final Setting<Integer> maxFileSizeMB = sgOutput.add(new IntSetting.Builder()
        .name("max-file-size-mb")
        .description("Maximum size of a single packet log file before rotation.")
        .defaultValue(10)
        .min(1)
        .sliderRange(1, 100)
        .visible(logToFile::get)
        .build()
    );

    private final Setting<Integer> maxTotalLogsMB = sgOutput.add(new IntSetting.Builder()
        .name("max-total-logs-mb")
        .description("Maximum total size of all THM packet logs before deleting the oldest files.")
        .defaultValue(100)
        .min(1)
        .sliderRange(1, 500)
        .visible(logToFile::get)
        .build()
    );

    private final Object2IntOpenHashMap<PacketType<? extends Packet<?>>> packetCounts = new Object2IntOpenHashMap<>();
    private final List<Path> sessionFiles = new ArrayList<>();

    private BufferedWriter fileWriter;
    private Path currentFilePath;
    private LocalDateTime sessionFileTime;
    private Instant sessionStartInstant;
    private long sessionStartNano;
    private long lastFlushMs;
    private long currentFileSizeBytes;
    private long ordinalCounter;
    private int currentFileIndex;
    private int serializationErrorCount;
    private int fileWriteErrorCount;

    public PacketLoggerTHM() {
        super(THMAddon.MAIN, "packet-logger-thm", "Logs selected packets to replay-grade JSONL files.");
        runInMainMenu = true;
    }

    @Override
    public void onActivate() {
        closeFileWriter();

        packetCounts.clear();
        sessionFiles.clear();
        currentFilePath = null;
        sessionFileTime = LocalDateTime.now();
        sessionStartInstant = Instant.now();
        sessionStartNano = System.nanoTime();
        lastFlushMs = System.currentTimeMillis();
        currentFileSizeBytes = 0;
        ordinalCounter = 0;
        currentFileIndex = 0;
        serializationErrorCount = 0;
        fileWriteErrorCount = 0;

        if (logToFile.get()) {
            try {
                Files.createDirectories(PACKET_LOGS_DIR);
                cleanupOldLogs();
                openNewLogFile();
                writeJsonRecord(buildStartRecord());
            } catch (IOException e) {
                error("Failed to initialize THM packet logging: %s", e.getMessage());
                fileWriteErrorCount++;
                closeFileWriter();
            }
        }
    }

    @Override
    public void onDeactivate() {
        if (logToFile.get() && fileWriter != null) writeJsonRecord(buildSummaryRecord());
        if (showSummary.get() && logToChat.get() && !packetCounts.isEmpty()) logSummaryToChat();
        closeFileWriter();
    }

    @EventHandler(priority = EventPriority.HIGHEST + 1)
    private void onReceivePacket(PacketEvent.Receive event) {
        if (s2cPackets.get().contains(event.packet.type())) logPacket("s2c", "<- S2C", event.packet);
    }

    @EventHandler(priority = EventPriority.HIGHEST + 1)
    private void onSendPacket(PacketEvent.Send event) {
        if (c2sPackets.get().contains(event.packet.type())) logPacket("c2s", "-> C2S", event.packet);
    }

    private void logPacket(String dir, String chatDir, Packet<?> packet) {
        if (!logToChat.get() && !logToFile.get()) return;
        if (isLocationPacket(packet) && !xyz.thm.addon.utils.PrivacyGuard.allowsCoordinateExport()) return;

        PacketType<? extends Packet<?>> packetType = eventPacketType(packet);
        packetCounts.addTo(packetType, 1);

        long ordinal = ++ordinalCounter;
        if (logToChat.get()) logPacketToChat(chatDir, packetType);
        if (logToFile.get()) writeJsonRecord(buildPacketRecord(dir, ordinal, packet));
    }

    private static boolean isLocationPacket(Packet<?> packet) {
        return packet instanceof ServerboundMovePlayerPacket
            || packet instanceof ClientboundPlayerPositionPacket
            || packet instanceof ClientboundEntityPositionSyncPacket;
    }

    @SuppressWarnings("unchecked")
    private static PacketType<? extends Packet<?>> eventPacketType(Packet<?> packet) {
        return (PacketType<? extends Packet<?>>) packet.type();
    }

    private void logPacketToChat(String direction, PacketType<? extends Packet<?>> packetType) {
        StringBuilder line = new StringBuilder(96);

        if (showTimestamp.get()) {
            line.append('[')
                .append(WALL_TIME_FORMATTER.format(Instant.now()))
                .append("] ");
        }

        line.append(direction).append(' ').append(packetType);

        if (showCount.get()) {
            line.append(" (#").append(packetCounts.getInt(packetType)).append(')');
        }

        info(line.toString());
    }

    private void logSummaryToChat() {
        int totalPackets = packetCounts.values().intStream().sum();
        info("--- THM Packet Logger Summary ---");
        info("Total packets logged: %d", totalPackets);

        packetCounts.object2IntEntrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getIntValue(), a.getIntValue()))
            .forEach(entry -> info("%s: %d", entry.getKey(), entry.getIntValue()));
    }

    private JsonObject buildStartRecord() {
        JsonObject record = baseRecord("start");
        record.addProperty("username", mc.getUser().getName());
        record.addProperty("singleplayer", mc.isLocalServer());
        addNullableString(record, "server_address", mc.getCurrentServer() != null ? mc.getCurrentServer().ip : null);
        addNullableString(record, "dimension", mc.level != null ? mc.level.dimension().identifier().toString() : null);
        record.addProperty("addon_version", THMAddon.VERSION);
        record.addProperty("game_version", SharedConstants.getCurrentVersion().name());
        record.add("settings", buildSettingsSnapshot());
        return record;
    }

    private JsonObject buildSummaryRecord() {
        JsonObject record = baseRecord("summary");
        record.addProperty("elapsed_ms", getMonotonicMillis());
        record.addProperty("total_packets", packetCounts.values().intStream().sum());
        record.add("packet_counts", buildPacketCountsArray());
        record.add("output_files", buildOutputFilesArray());
        record.addProperty("serialization_error_count", serializationErrorCount);
        record.addProperty("file_write_error_count", fileWriteErrorCount);
        return record;
    }

    private JsonObject buildPacketRecord(String dir, long ordinal, Packet<?> packet) {
        JsonObject record = baseRecord("packet");
        PacketType<? extends Packet<?>> packetType = eventPacketType(packet);

        record.addProperty("dir", dir);
        record.addProperty("ordinal", ordinal);
        record.addProperty("packet_class", packet.getClass().getName());
        record.addProperty("packet_name", packetType.toString());

        JsonObject fields;
        try {
            fields = serializePacket(packet);
        } catch (Exception e) {
            serializationErrorCount++;
            fields = new JsonObject();
            fields.addProperty("serialization_error", e.toString());
        }

        record.add("fields", fields);

        if (captureRawToString.get()) {
            try {
                String raw = String.valueOf(packet);
                if (xyz.thm.addon.utils.PrivacyGuard.containsSecrets(raw)
                    || (!xyz.thm.addon.utils.PrivacyGuard.allowsCoordinateExport() && isLocationPacket(packet))) {
                    record.addProperty("raw_to_string", "<redacted>");
                } else {
                    record.addProperty("raw_to_string", raw);
                }
            } catch (Exception e) {
                record.addProperty("raw_to_string", "<toString failed: " + e.getClass().getSimpleName() + ">");
            }
        }

        return record;
    }

    private JsonObject baseRecord(String kind) {
        JsonObject record = new JsonObject();
        record.addProperty("kind", kind);
        record.addProperty("schema_version", SCHEMA_VERSION);
        record.addProperty("ts_wall", WALL_TIME_FORMATTER.format(Instant.now()));
        record.addProperty("ts_mono_ms", getMonotonicMillis());
        return record;
    }

    private JsonObject buildSettingsSnapshot() {
        JsonObject settingsJson = new JsonObject();
        settingsJson.addProperty("log_to_file", logToFile.get());
        settingsJson.addProperty("log_to_chat", logToChat.get());
        settingsJson.addProperty("show_timestamp", showTimestamp.get());
        settingsJson.addProperty("show_count", showCount.get());
        settingsJson.addProperty("show_summary", showSummary.get());
        settingsJson.addProperty("capture_raw_to_string", captureRawToString.get());
        settingsJson.addProperty("flush_interval_s", flushInterval.get());
        settingsJson.addProperty("max_file_size_mb", maxFileSizeMB.get());
        settingsJson.addProperty("max_total_logs_mb", maxTotalLogsMB.get());
        settingsJson.add("c2s_packets", packetNamesToJsonArray(c2sPackets.get()));
        settingsJson.add("s2c_packets", packetNamesToJsonArray(s2cPackets.get()));
        return settingsJson;
    }

    private JsonArray buildPacketCountsArray() {
        JsonArray counts = new JsonArray();
        packetCounts.object2IntEntrySet().stream()
            .sorted((a, b) -> Integer.compare(b.getIntValue(), a.getIntValue()))
            .forEach(entry -> {
                JsonObject packetCount = new JsonObject();
                packetCount.addProperty("packet_class", entry.getKey().toString());
                packetCount.addProperty("packet_name", entry.getKey().toString());
                packetCount.addProperty("count", entry.getIntValue());
                counts.add(packetCount);
            });
        return counts;
    }

    private JsonArray buildOutputFilesArray() {
        JsonArray files = new JsonArray();
        for (Path path : sessionFiles) {
            files.add(path.toAbsolutePath().toString());
        }
        return files;
    }

    private JsonArray packetNamesToJsonArray(Set<PacketType<? extends Packet<?>>> packets) {
        JsonArray array = new JsonArray();
        packets.stream()
            .map(PacketType::toString)
            .sorted()
            .forEach(array::add);
        return array;
    }

    private JsonObject serializePacket(Packet<?> packet) {
        JsonObject fields = new JsonObject();

        if (packet instanceof ServerboundPlayerActionPacket p) {
            fields.addProperty("action", p.getAction().name());
            fields.add("block_pos", serializeBlockPos(p.getPos()));
            fields.addProperty("direction", p.getDirection().name());
            fields.addProperty("sequence", p.getSequence());
            return fields;
        }

        if (packet instanceof ServerboundUseItemOnPacket p) {
            BlockHitResult hit = p.getHitResult();
            fields.addProperty("hand", p.getHand().name());
            fields.add("block_pos", serializeBlockPos(hit.getBlockPos()));
            fields.addProperty("side", hit.getDirection().name());
            fields.add("hit_vec", serializeVec3d(hit.getLocation()));
            fields.addProperty("inside_block", hit.isInside());
            fields.addProperty("sequence", p.getSequence());
            return fields;
        }

        if (packet instanceof ServerboundUseItemPacket p) {
            fields.addProperty("hand", p.getHand().name());
            fields.addProperty("sequence", p.getSequence());
            fields.addProperty("yaw", p.getYRot());
            fields.addProperty("pitch", p.getXRot());
            return fields;
        }

        if (packet instanceof ServerboundSetCarriedItemPacket p) {
            fields.addProperty("slot", p.getSlot());
            return fields;
        }

        if (packet instanceof ServerboundContainerClickPacket p) {
            fields.addProperty("sync_id", p.containerId());
            fields.addProperty("revision", p.stateId());
            fields.addProperty("slot", p.slotNum());
            fields.addProperty("button", p.buttonNum());
            fields.addProperty("action_type", p.containerInput().name());
            fields.add("cursor", serializeItemStackHash(p.carriedItem()));
            fields.add("changed_stacks", serializeChangedStackHashes(p.changedSlots()));
            return fields;
        }

        if (packet instanceof ServerboundSwingPacket p) {
            fields.addProperty("hand", p.getHand().name());
            return fields;
        }

        if (packet instanceof ServerboundMovePlayerPacket p) {
            fields.addProperty("subtype", getMoveSubtype(p));
            fields.addProperty("changes_position", p.hasPosition());
            fields.addProperty("changes_look", p.hasRotation());
            fields.addProperty("on_ground", p.isOnGround());
            fields.addProperty("horizontal_collision", p.horizontalCollision());
            if (p.hasPosition()) {
                if (xyz.thm.addon.utils.PrivacyGuard.allowsCoordinateExport()) {
                    fields.addProperty("x", p.getX(Double.NaN));
                    fields.addProperty("y", p.getY(Double.NaN));
                    fields.addProperty("z", p.getZ(Double.NaN));
                } else {
                    fields.addProperty("redacted", true);
                }
            }
            if (p.hasRotation()) {
                fields.addProperty("yaw", p.getYRot(Float.NaN));
                fields.addProperty("pitch", p.getXRot(Float.NaN));
            }
            return fields;
        }

        if (packet instanceof ServerboundPlayerInputPacket p) {
            Input input = p.input();
            fields.addProperty("forward", input.forward());
            fields.addProperty("backward", input.backward());
            fields.addProperty("left", input.left());
            fields.addProperty("right", input.right());
            fields.addProperty("jump", input.jump());
            fields.addProperty("sneak", input.shift());
            fields.addProperty("sprint", input.sprint());
            return fields;
        }

        if (packet instanceof ServerboundPlayerCommandPacket p) {
            fields.addProperty("mode", p.getAction().name());
            fields.addProperty("entity_id", p.getId());
            fields.addProperty("mount_jump_height", p.getData());
            return fields;
        }

        if (packet instanceof ClientboundContainerSetSlotPacket p) {
            fields.addProperty("sync_id", p.getContainerId());
            fields.addProperty("slot", p.getSlot());
            fields.addProperty("revision", p.getStateId());
            fields.add("stack", serializeItemStack(p.getItem()));
            return fields;
        }

        if (packet instanceof ClientboundBlockUpdatePacket p) {
            fields.add("block_pos", serializeBlockPos(p.getPos()));
            JsonObject state = new JsonObject();
            state.addProperty("block_id", BuiltInRegistries.BLOCK.getKey(p.getBlockState().getBlock()).toString());
            state.addProperty("state", p.getBlockState().toString());
            fields.add("block_state", state);
            return fields;
        }

        if (packet instanceof ClientboundBlockChangedAckPacket p) {
            fields.addProperty("sequence", p.sequence());
            return fields;
        }

        return fields;
    }

    private JsonArray serializeChangedStackHashes(Int2ObjectMap<HashedStack> modifiedStacks) {
        JsonArray stacks = new JsonArray();
        modifiedStacks.int2ObjectEntrySet().stream()
            .sorted(Comparator.comparingInt(Int2ObjectMap.Entry::getIntKey))
            .forEach(entry -> {
                JsonObject stackEntry = new JsonObject();
                stackEntry.addProperty("slot", entry.getIntKey());
                stackEntry.add("stack", serializeItemStackHash(entry.getValue()));
                stacks.add(stackEntry);
            });
        return stacks;
    }

    private JsonObject serializeItemStackHash(HashedStack stackHash) {
        JsonObject json = new JsonObject();
        json.addProperty("empty", stackHash == null || stackHash == HashedStack.EMPTY);
        if (stackHash == null || stackHash == HashedStack.EMPTY) return json;

        if (stackHash instanceof HashedStack.ActualItem impl) {
            json.addProperty("item_id", getRegistryEntryId(impl.item()));
            json.addProperty("count", impl.count());
            json.addProperty("components", String.valueOf(impl.components()));
        } else {
            json.addProperty("raw", String.valueOf(stackHash));
        }

        return json;
    }

    private JsonObject serializeItemStack(ItemStack stack) {
        JsonObject json = new JsonObject();
        json.addProperty("empty", stack == null || stack.isEmpty());
        if (stack == null || stack.isEmpty()) return json;

        json.addProperty("item_id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        json.addProperty("count", stack.getCount());
        json.addProperty("damage", stack.getDamageValue());
        json.addProperty("max_damage", stack.getMaxDamage());
        json.addProperty("damageable", stack.isDamageableItem());

        Component customName = stack.getCustomName();
        if (customName != null) json.addProperty("custom_name", customName.getString());
        else json.add("custom_name", JsonNull.INSTANCE);

        json.add("enchantments", serializeEnchantments(stack.getEnchantments()));

        if (mc.level != null) {
            HolderLookup.Provider lookup = (HolderLookup.Provider) mc.level.registryAccess();
            Tag nbt = ItemStack.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, lookup), stack).result().orElse(null);
            if (nbt != null) json.addProperty("nbt_snbt", nbt.asString().orElse(""));
            else json.add("nbt_snbt", JsonNull.INSTANCE);
        } else {
            json.add("nbt_snbt", JsonNull.INSTANCE);
        }

        return json;
    }

    private JsonArray serializeEnchantments(ItemEnchantments enchantments) {
        JsonArray array = new JsonArray();
        List<JsonObject> entries = new ArrayList<>();

        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            JsonObject enchantment = new JsonObject();
            enchantment.addProperty("id", getEnchantmentId(entry.getKey()));
            enchantment.addProperty("level", entry.getIntValue());
            entries.add(enchantment);
        }

        entries.stream()
            .sorted(Comparator.comparing(e -> e.get("id").getAsString()))
            .forEach(array::add);

        return array;
    }

    private String getEnchantmentId(Holder<Enchantment> entry) {
        return entry.unwrapKey()
            .map(ResourceKey::identifier)
            .map(Identifier::toString)
            .orElse(entry.toString());
    }

    private <T> String getRegistryEntryId(Holder<T> entry) {
        return entry.unwrapKey()
            .map(ResourceKey::identifier)
            .map(Identifier::toString)
            .orElse(entry.toString());
    }

    private JsonObject serializeBlockPos(BlockPos pos) {
        JsonObject json = new JsonObject();
        if (!xyz.thm.addon.utils.PrivacyGuard.allowsCoordinateExport()) {
            json.addProperty("redacted", true);
            return json;
        }
        json.addProperty("x", pos.getX());
        json.addProperty("y", pos.getY());
        json.addProperty("z", pos.getZ());
        return json;
    }

    private JsonObject serializeVec3d(Vec3 vec) {
        JsonObject json = new JsonObject();
        if (!xyz.thm.addon.utils.PrivacyGuard.allowsCoordinateExport()) {
            json.addProperty("redacted", true);
            return json;
        }
        json.addProperty("x", vec.x);
        json.addProperty("y", vec.y);
        json.addProperty("z", vec.z);
        return json;
    }

    private String getMoveSubtype(ServerboundMovePlayerPacket packet) {
        if (packet instanceof ServerboundMovePlayerPacket.PosRot) return "full";
        if (packet instanceof ServerboundMovePlayerPacket.Rot) return "look_and_on_ground";
        if (packet instanceof ServerboundMovePlayerPacket.Pos) return "position_and_on_ground";
        if (packet instanceof ServerboundMovePlayerPacket.StatusOnly) return "on_ground_only";
        return "base";
    }

    private void writeJsonRecord(JsonObject record) {
        if (fileWriter == null) return;

        try {
            String line = GSON.toJson(record);
            int lineBytes = line.getBytes(StandardCharsets.UTF_8).length + LINE_SEPARATOR_BYTES;

            if (currentFileSizeBytes + lineBytes > maxFileSizeMB.get() * 1024L * 1024L) {
                openNewLogFile();
            }

            fileWriter.write(line);
            fileWriter.newLine();
            currentFileSizeBytes += lineBytes;

            long now = System.currentTimeMillis();
            if (now - lastFlushMs >= flushInterval.get() * 1000L) {
                fileWriter.flush();
                lastFlushMs = now;
            }
        } catch (IOException e) {
            fileWriteErrorCount++;
            error("Failed to write packet log file: %s", e.getMessage());
            closeFileWriter();
        }
    }

    private void openNewLogFile() throws IOException {
        closeFileWriter();

        String fileName = "packets-%s-%d.jsonl".formatted(sessionFileTime.format(FILE_NAME_FORMATTER), currentFileIndex++);
        Path path = PACKET_LOGS_DIR.resolve(fileName);

        fileWriter = Files.newBufferedWriter(
            path,
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE
        );

        currentFilePath = path;
        currentFileSizeBytes = 0;
        lastFlushMs = System.currentTimeMillis();
        sessionFiles.add(path);
        cleanupOldLogs();
    }

    private void closeFileWriter() {
        if (fileWriter == null) return;

        try {
            fileWriter.flush();
            fileWriter.close();
        } catch (IOException ignored) {
            // Safe to ignore on shutdown/rotation.
        } finally {
            fileWriter = null;
        }
    }

    private void cleanupOldLogs() throws IOException {
        long maxBytes = maxTotalLogsMB.get() * 1024L * 1024L;
        List<LogFileEntry> logFiles = new ArrayList<>();

        if (!Files.isDirectory(PACKET_LOGS_DIR)) return;

        try (var stream = Files.list(PACKET_LOGS_DIR)) {
            for (Path path : stream.toList()) {
                String name = path.getFileName().toString();
                if (!name.startsWith("packets-") || !name.endsWith(".jsonl")) continue;

                try {
                    logFiles.add(new LogFileEntry(
                        path,
                        Files.size(path),
                        Files.getLastModifiedTime(path).toMillis()
                    ));
                } catch (IOException ignored) {
                    // Skip inaccessible files.
                }
            }
        }

        logFiles.sort(Comparator.comparingLong(LogFileEntry::lastModified));
        long totalSize = logFiles.stream().mapToLong(LogFileEntry::size).sum();

        for (LogFileEntry entry : logFiles) {
            if (totalSize <= maxBytes) break;
            if (sessionFiles.contains(entry.path)) continue;

            if (Files.deleteIfExists(entry.path)) {
                totalSize -= entry.size;
            }
        }
    }

    private long getMonotonicMillis() {
        return (System.nanoTime() - sessionStartNano) / 1_000_000L;
    }

    private void addNullableString(JsonObject object, String key, String value) {
        if (value == null) object.add(key, JsonNull.INSTANCE);
        else object.addProperty(key, value);
    }

    private record LogFileEntry(Path path, long size, long lastModified) {
    }
}
