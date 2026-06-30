package xyz.thm.addon.system;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.System;
import meteordevelopment.meteorclient.systems.Systems;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.modules.HighwayBuilderTHM;
import xyz.thm.addon.utils.APIUtils;
import xyz.thm.addon.utils.KitbotChatRouter;
import xyz.thm.addon.utils.ThmMembers;
import xyz.thm.addon.waveycapes.CapeStyle;
import xyz.thm.addon.waveycapes.WaveyCapesConfig;
import xyz.thm.addon.waveycapes.WindMode;

public class THMSystem extends System<THMSystem> {
    public final Settings settings = new Settings();

    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgPrefix = settings.createGroup("Hash");
    private final SettingGroup sgProfiles = settings.createGroup("Highway Profiles");
    private final SettingGroup sgPvp = settings.createGroup("PVP");
    private final SettingGroup sgRender = settings.createGroup("THM Rendering");
    private final SettingGroup sgKitbot = settings.createGroup("KitBot");

    // Separate settings object for the Wavy Capes screen (not shown in the main THM tab)
    public final Settings wavyCapesSettings = new Settings();
    private final SettingGroup sgWavyCapes = wavyCapesSettings.createGroup("Wavy Capes");

    // General Settings
    public final Setting<Boolean> screenshotToClipboard = sgGeneral.add(new BoolSetting.Builder()
        .name("screenshot-to-clipboard")
        .description("Automatically copies screenshots to the clipboard when taken.")
        .defaultValue(true)
        .build()
    );

    // Hash Settings
    private final Setting<String> hash = sgPrefix.add(new StringSetting.Builder()
        .name("Hash")
        .description("The Hash that you got")
        .defaultValue("SetYourHash")
        .build()
    );

    private final Setting<String> crackedPassword = sgPrefix.add(new StringSetting.Builder()
        .name("cracked-password")
        .description("Password used for cracked-account reconnect /login.")
        .defaultValue("")
        .build()
    );

    // Highway Profiles Settings
    public final Setting<Mode> mode = sgProfiles.add(new EnumSetting.Builder<Mode>()
        .name("profile")
        .description("Which highway profile to use.")
        .defaultValue(Mode.None)
        .build()
    );

    private final Setting<Boolean> toggleModules = sgProfiles.add(new BoolSetting.Builder()
        .name("toggle-modules")
        .description("Turn on Highwaybuilder when toggled.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> ignoreThmMembers = sgPvp.add(new BoolSetting.Builder()
        .name("ignore-thm-members")
        .description("Ignore THM members in PvP modules.")
        .defaultValue(true)
        .build()
    );

    public static final String BRANCH_ALL = "All";
    public static final String BRANCH_MAIN = "Main";
    public static final String BRANCH_PVP = "PvP";

    public final Setting<String> showBranch = sgPvp.add(new ProvidedStringSetting.Builder()
        .name("show-branch")
        .description("Which branch members to show in THM member lists.")
        .defaultValue(BRANCH_ALL)
        .supplier(() -> new String[] { BRANCH_ALL, BRANCH_MAIN, BRANCH_PVP })
        .build()
    );

    public final Setting<Boolean> highlightInTab = sgRender.add(new BoolSetting.Builder()
        .name("highlight-in-tab")
        .description("Highlights THM members in the player tab list.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> highlightNametags = sgRender.add(new BoolSetting.Builder()
        .name("highlight-nametags")
        .description("Highlights THM members in nametags.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> useRankColor = sgRender.add(new BoolSetting.Builder()
        .name("use-rank-color")
        .description("Use the member's rank color instead of a single highlight color.")
        .defaultValue(true)
        .build()
    );

    public final Setting<meteordevelopment.meteorclient.utils.render.color.SettingColor> highlightColor = sgRender.add(new ColorSetting.Builder()
        .name("highlight-color")
        .description("Highlight color for THM members.")
        .defaultValue(new meteordevelopment.meteorclient.utils.render.color.SettingColor(255, 217, 94, 255))
        .visible(() -> !useRankColor.get())
        .build()
    );

    public final Setting<Boolean> showNametagIcon = sgRender.add(new BoolSetting.Builder()
        .name("show-nametag-icon")
        .description("Shows the THM icon before member names in nametags.")
        .defaultValue(true)
        .build()
    );

    public final Setting<Boolean> kitbotChatRouterEnabled = sgKitbot.add(new BoolSetting.Builder()
        .name("kitbot-chat-router")
        .description("Routes recognized $kitbot chat commands through Kitbot Frontend.")
        .defaultValue(true)
        .onChanged(KitbotChatRouter::setEnabled)
        .build()
    );
    public final Setting<Type> nametagType = sgRender.add(new EnumSetting.Builder<Type>()
        .name("Icon Type")
        .description("Select the nametag rendering style")
        .defaultValue(Type.TransparentWhite)
        .visible(showNametagIcon::get)
        .build()
    );

    public final Setting<CapeType> cape = sgRender.add(new EnumSetting.Builder<CapeType>()
        .name("thm-cape")
        .description("Cape shown on yourself and other THM members.")
        .defaultValue(CapeType.None)
        .onChanged(ct -> {
            if (FabricLoader.getInstance().isDevelopmentEnvironment()) return;
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return;
            if (!ThmMembers.isThmMember(mc.player)) return;
            String username = mc.player.getGameProfile().name();
            APIUtils.postCapeSelection(username, ct.toApiId(), getHash());
        })
        .build()
    );

    public final Setting<Boolean> wavyCapes = sgWavyCapes.add(new BoolSetting.Builder()
        .name("wavy-capes")
        .description("Replaces the vanilla stiff cape with rope physics. Off = vanilla behavior.")
        .defaultValue(false)
        .onChanged(v -> WaveyCapesConfig.syncFromSystem())
        .build()
    );

    public final Setting<CapeStyle> wavyCapeStyle = sgWavyCapes.add(new EnumSetting.Builder<CapeStyle>()
        .name("rendering")
        .description("Blocky: 16 rigid segments. Smooth: interpolated quads between segments.")
        .defaultValue(CapeStyle.SMOOTH)
        .visible(wavyCapes::get)
        .onChanged(v -> WaveyCapesConfig.syncFromSystem())
        .build()
    );

    public final Setting<WindMode> wavyWindMode = sgWavyCapes.add(new EnumSetting.Builder<WindMode>()
        .name("wind")
        .description("None: cape only moves from player motion. Waves: adds a gentle idle sway.")
        .defaultValue(WindMode.NONE)
        .visible(wavyCapes::get)
        .onChanged(v -> WaveyCapesConfig.syncFromSystem())
        .build()
    );

    public final Setting<Double> wavyGravity = sgWavyCapes.add(new DoubleSetting.Builder()
        .name("gravity")
        .description("How hard gravity pulls the cape down. Higher = heavier cape.")
        .defaultValue(25)
        .min(0).max(100).sliderRange(0, 100)
        .visible(wavyCapes::get)
        .onChanged(v -> WaveyCapesConfig.syncFromSystem())
        .build()
    );

    public final Setting<Double> wavyHeightMultiplier = sgWavyCapes.add(new DoubleSetting.Builder()
        .name("vertical-response")
        .description("How much jumping or falling tosses the cape upward.")
        .defaultValue(6)
        .min(0).max(20).sliderRange(0, 20)
        .visible(wavyCapes::get)
        .onChanged(v -> WaveyCapesConfig.syncFromSystem())
        .build()
    );

    public final Setting<Double> wavyStraveMultiplier = sgWavyCapes.add(new DoubleSetting.Builder()
        .name("strafe-response")
        .description("How much strafing flares the cape sideways.")
        .defaultValue(2)
        .min(0).max(10).sliderRange(0, 10)
        .visible(wavyCapes::get)
        .onChanged(v -> WaveyCapesConfig.syncFromSystem())
        .build()
    );

    public final Setting<Double> wavyDamping = sgWavyCapes.add(new DoubleSetting.Builder()
        .name("damping")
        .description("Velocity damping per tick — lower = more floaty.")
        .defaultValue(0.85)
        .min(0).max(1).sliderRange(0, 1)
        .visible(wavyCapes::get)
        .onChanged(v -> WaveyCapesConfig.syncFromSystem())
        .build()
    );

    public final Setting<Integer> wavyStiffness = sgWavyCapes.add(new IntSetting.Builder()
        .name("stiffness")
        .description("Constraint solver iterations — higher = stiffer cape.")
        .defaultValue(3)
        .min(1).max(10).sliderRange(1, 10)
        .visible(wavyCapes::get)
        .onChanged(v -> WaveyCapesConfig.syncFromSystem())
        .build()
    );

    public final Setting<Double> wavyMaxBend = sgWavyCapes.add(new DoubleSetting.Builder()
        .name("max-bend")
        .description("Maximum bend angle between cape segments (degrees).")
        .defaultValue(20)
        .min(1).max(90).sliderRange(1, 90)
        .visible(wavyCapes::get)
        .onChanged(v -> WaveyCapesConfig.syncFromSystem())
        .build()
    );

    // Store original values
    private int savedWidth = -1;
    private int savedHeight = -1;
    private boolean savedMineAboveRailings = false;
    private boolean savedBuildRailings = false;
    private java.util.List<net.minecraft.block.Block> savedBlocksToPlace = null;

    public THMSystem() {
        super("THM-Addon");
        KitbotChatRouter.setEnabled(kitbotChatRouterEnabled.get());
        WaveyCapesConfig.syncFromSystem();
    }

    public static THMSystem get() {
        return Systems.get(THMSystem.class);
    }

    public String getHash() {
        return hash.get();
    }

    public String getCrackedPassword() {
        return crackedPassword.get();
    }

    public void applyProfile() {
        HighwayBuilderTHM hwBuilder = Modules.get().get(HighwayBuilderTHM.class);
        if (hwBuilder == null) return;

        switch (mode.get()) {
            case None -> {
                // Only restore if values were previously saved
                if (savedWidth != -1) {
                    hwBuilder.width.set(savedWidth);
                    hwBuilder.height.set(savedHeight);
                    hwBuilder.mineAboveRailings.set(savedMineAboveRailings);
                    hwBuilder.blocksToPlace.set(savedBlocksToPlace);
                    hwBuilder.railings.set(savedBuildRailings);
                }
            }
            case HighwayBuilding -> {
                // Save original values before changing
                savedWidth = hwBuilder.width.get();
                savedHeight = hwBuilder.height.get();
                savedMineAboveRailings = hwBuilder.mineAboveRailings.get();
                savedBlocksToPlace = hwBuilder.blocksToPlace.get();
                savedBuildRailings = hwBuilder.railings.get();

                hwBuilder.width.set(5);
                hwBuilder.height.set(3);
                hwBuilder.blocksToPlace.set(java.util.List.of(Blocks.OBSIDIAN));
                hwBuilder.mineAboveRailings.set(true);
                hwBuilder.railings.set(true);
                hwBuilder.kitbotEChestRestockKit.set(HighwayBuilderTHM.KitbotEChestRestockKit.Highway);
                hwBuilder.kitbotPickaxeRestockKit.set(HighwayBuilderTHM.KitbotPickaxeRestockKit.Highway);
            }
            case HighwayDigging -> {
                // Save original values before changing
                savedWidth = hwBuilder.width.get();
                savedHeight = hwBuilder.height.get();
                savedMineAboveRailings = hwBuilder.mineAboveRailings.get();
                savedBlocksToPlace = hwBuilder.blocksToPlace.get();
                savedBuildRailings = hwBuilder.railings.get();

                hwBuilder.blocksToPlace.set(java.util.List.of(Blocks.NETHERRACK, Blocks.BASALT, Blocks.BLACKSTONE));
                hwBuilder.width.set(5);
                hwBuilder.height.set(4);
                hwBuilder.mineAboveRailings.set(true);
                hwBuilder.railings.set(true);
                hwBuilder.kitbotPickaxeRestockKit.set(HighwayBuilderTHM.KitbotPickaxeRestockKit.Pickaxe);
            }
        }
        if (toggleModules.get() && !hwBuilder.isActive()) {
            hwBuilder.toggle();
        }
    }

    public void restoreProfile() {
        HighwayBuilderTHM hwBuilder = Modules.get().get(HighwayBuilderTHM.class);
        if (hwBuilder == null) return;
        if (toggleModules.get() && hwBuilder.isActive()) {
            hwBuilder.toggle();
        }
    }

    @Override
    public NbtCompound toTag() {
        NbtCompound tag = new NbtCompound();
        tag.putString("version", THMAddon.VERSION);
        tag.put("settings", settings.toTag());
        tag.put("wavyCapesSettings", wavyCapesSettings.toTag());
        return tag;
    }

    @Override
    public THMSystem fromTag(NbtCompound tag) {
        if (tag.contains("settings")) {
            settings.fromTag(tag.getCompound("settings").orElse(new NbtCompound()));
        }
        if (tag.contains("wavyCapesSettings")) {
            wavyCapesSettings.fromTag(tag.getCompound("wavyCapesSettings").orElse(new NbtCompound()));
        }
        KitbotChatRouter.setEnabled(kitbotChatRouterEnabled.get());
        WaveyCapesConfig.syncFromSystem();
        return this;
    }

    public enum Mode {
        None,
        HighwayBuilding,
        HighwayDigging
    }
    public enum Type {
        Obby,
        TransparentWhite,
        TransparentBlack
    }

    public enum CapeType {
        None,
        Thm3,
        Thm4;

        public String toApiId() {
            return this.name().toLowerCase(java.util.Locale.ROOT);
        }
    }
}
