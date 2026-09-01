/*
 * This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
 * Copyright (c) THM Addons contributors. Credit the devs, keep the link.
 * By using this code you agree to the license terms and to keep your repo public.
 */

package xyz.thm.addon.modules;

import meteordevelopment.discordipc.DiscordIPC;
import meteordevelopment.discordipc.RichPresence;
import meteordevelopment.meteorclient.events.game.OpenScreenEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.utils.StarscriptTextBoxRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.misc.DiscordPresence;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.MeteorStarscript;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.client.gui.screens.options.SoundOptionsScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.gui.screens.options.controls.ControlsScreen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.AbstractGameRulesScreen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.realms.RealmsScreen;
import net.minecraft.util.Util;
import org.meteordev.starscript.Script;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.compat.ClientGui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class DiscordRPC extends Module {
    private final SettingGroup sgHighway = settings.createGroup("Highway");
    private final SettingGroup sgLine1 = settings.createGroup("Line 1");
    private final SettingGroup sgLine2 = settings.createGroup("Line 2");

    private final Setting<Boolean> showHighwayStats = sgHighway.add(new BoolSetting.Builder()
        .name("highway-stats")
        .description("Overrides both lines with live HighwayBuilderTHM stats when active.")
        .defaultValue(true)
        .build()
    );

    // Line 1

    private final Setting<List<String>> line1Strings = sgLine1.add(new StringListSetting.Builder()
        .name("1st-line-messages")
        .description("Messages used for the first line.")
        .defaultValue("Playing {server}")
        .onChanged(strings -> recompileLine1())
        .renderer(StarscriptTextBoxRenderer.class)
        .build()
    );

    private final Setting<Integer> line1UpdateDelay = sgLine1.add(new IntSetting.Builder()
        .name("1st-line-update-delay")
        .description("How fast to update the first line in ticks.")
        .defaultValue(200)
        .min(10)
        .sliderRange(10, 200)
        .build()
    );

    private final Setting<SelectMode> line1SelectMode = sgLine1.add(new EnumSetting.Builder<SelectMode>()
        .name("1st-line-select-mode")
        .description("How to select messages for the first line.")
        .defaultValue(SelectMode.Sequential)
        .build()
    );

    // Line 2

    private final Setting<List<String>> line2Strings = sgLine2.add(new StringListSetting.Builder()
        .name("2nd-line-messages")
        .description("Messages used for the second line.")
        .defaultValue("{player}", "Working on highways", "{server.player_count} players online")
        .onChanged(strings -> recompileLine2())
        .renderer(StarscriptTextBoxRenderer.class)
        .build()
    );

    private final Setting<Integer> line2UpdateDelay = sgLine2.add(new IntSetting.Builder()
        .name("2nd-line-update-delay")
        .description("How fast to update the second line in ticks.")
        .defaultValue(70)
        .min(10)
        .sliderRange(10, 200)
        .build()
    );

    private final Setting<SelectMode> line2SelectMode = sgLine2.add(new EnumSetting.Builder<SelectMode>()
        .name("2nd-line-select-mode")
        .description("How to select messages for the second line.")
        .defaultValue(SelectMode.Sequential)
        .build()
    );

    private static final RichPresence rpc = new RichPresence();
    private int ticks;
    private boolean forceUpdate, lastWasInMainMenu;

    private final List<Script> line1Scripts = new ArrayList<>();
    private int line1Ticks, line1I;

    private final List<Script> line2Scripts = new ArrayList<>();
    private int line2Ticks, line2I;

    public static final LinkedHashMap<String, String> customStates = new LinkedHashMap<>();

    static {
        registerCustomState("com.terraformersmc.modmenu.gui", "Browsing mods");
        registerCustomState("me.jellysquid.mods.sodium.client", "Changing options");
    }

    public DiscordRPC() {
        super(THMAddon.MAIN, "discord-RPC", "Displays the THM Addon as an activity on Discord. Coordinate placeholders are stripped, and live highway stats only publish while paving the official nether highway.");

        runInMainMenu = true;
    }

    public static void registerCustomState(String packageName, String state) {
        customStates.put(packageName, state);
    }

    public static void unregisterCustomState(String packageName) {
        customStates.remove(packageName);
    }

    @Override
    public void onActivate() {
        checkRPC();

        DiscordIPC.start(1474806156022251612L, null);

        rpc.setStart(System.currentTimeMillis() / 1000L);
        String largeText = "THM Addon " + THMAddon.VERSION;
        rpc.setLargeImage("thm-addon", largeText);

        recompileLine1();
        recompileLine2();

        ticks = 0;
        line1Ticks = 0;
        line2Ticks = 0;
        lastWasInMainMenu = false;

        line1I = 0;
        line2I = 0;
    }

    public void checkRPC() {
        if (ClientGui.screen(mc) == null) return;
        DiscordPresence presence = Modules.get().get(DiscordPresence.class);
        if (presence != null && presence.isActive()) presence.toggle();
    }

    @Override
    public void onDeactivate() {
        DiscordIPC.stop();
    }

    private void recompile(List<String> messages, List<Script> scripts) {
        scripts.clear();

        for (String message : messages) {
            if (templateCanLeakCoordinates(message)) continue;
            Script script = MeteorStarscript.compile(message);
            if (script != null) scripts.add(script);
        }

        forceUpdate = true;
    }

    private void recompileLine1() {
        recompile(line1Strings.get(), line1Scripts);
    }

    private void recompileLine2() {
        recompile(line2Strings.get(), line2Scripts);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        boolean update = false;

        // Image
        if (ticks >= 200 || forceUpdate) {
            update = true;

            ticks = 0;
        } else ticks++;

        if (Utils.canUpdate()) {
            boolean highwayOverride = false;
            if (showHighwayStats.get()) {
                HighwayBuilderTHM hb = Modules.get().get(HighwayBuilderTHM.class);
                if (hb != null && hb.isActive() && xyz.thm.addon.utils.PrivacyGuard.allowsRemoteExport()) {
                    String dir = hb.dir != null ? hb.dir.name : "?";
                    double dist = mc.player != null && hb.start != null
                        ? mc.player.getEyePosition().distanceTo(hb.start) : 0;
                    rpc.setDetails("Building " + dir + " highway");
                    rpc.setState(String.format("P: %d | B: %d | %.0fm", hb.blocksPlaced, hb.blocksBroken, dist));
                    update = true;
                    highwayOverride = true;
                }
            }

            if (!highwayOverride) {
            // Line 1
            if (line1Ticks >= line1UpdateDelay.get() || forceUpdate) {
                if (!line1Scripts.isEmpty()) {
                    int i = Utils.random(0, line1Scripts.size());
                    if (line1SelectMode.get() == SelectMode.Sequential) {
                        if (line1I >= line1Scripts.size()) line1I = 0;
                        i = line1I++;
                    }

                    String message = sanitizeRpcText(MeteorStarscript.run(line1Scripts.get(i)));
                    if (message != null) rpc.setDetails(message);
                }
                update = true;

                line1Ticks = 0;
            } else line1Ticks++;

            // Line 2
            if (line2Ticks >= line2UpdateDelay.get() || forceUpdate) {
                if (!line2Scripts.isEmpty()) {
                    int i = Utils.random(0, line2Scripts.size());
                    if (line2SelectMode.get() == SelectMode.Sequential) {
                        if (line2I >= line2Scripts.size()) line2I = 0;
                        i = line2I++;
                    }

                    String message = sanitizeRpcText(MeteorStarscript.run(line2Scripts.get(i)));
                    if (message != null) rpc.setState(message);
                }
                update = true;

                line2Ticks = 0;
            } else line2Ticks++;
            } // end !highwayOverride
        } else {
            if (!lastWasInMainMenu) {
                rpc.setDetails("THM Addon " + THMAddon.VERSION);

                var screen = ClientGui.screen(mc);
                if (screen instanceof TitleScreen) rpc.setState("In main menu");
                else if (screen instanceof SelectWorldScreen) rpc.setState("Selecting world");
                else if (screen instanceof CreateWorldScreen || screen instanceof AbstractGameRulesScreen) rpc.setState("Creating world");
                else if (screen instanceof EditWorldScreen) rpc.setState("Editing world");
                else if (screen instanceof LevelLoadingScreen) rpc.setState("Loading world");
                else if (screen instanceof JoinMultiplayerScreen) rpc.setState("Selecting server");
                else if (screen instanceof ManageServerScreen) rpc.setState("Adding server");
                else if (screen instanceof ConnectScreen || screen instanceof DirectJoinServerScreen) rpc.setState("Connecting to server");
                else if (screen instanceof WidgetScreen) rpc.setState("Browsing Meteor's GUI");
                else if (screen instanceof OptionsScreen || screen instanceof SkinCustomizationScreen || screen instanceof SoundOptionsScreen || screen instanceof VideoSettingsScreen || screen instanceof ControlsScreen || screen instanceof LanguageSelectScreen || screen instanceof ChatOptionsScreen || screen instanceof PackSelectionScreen || screen instanceof AccessibilityOptionsScreen) rpc.setState("Changing options");
                else if (screen instanceof WinScreen) rpc.setState("Reading credits");
                else if (screen instanceof RealmsScreen) rpc.setState("Browsing Realms");
                else {
                    boolean setState = false;
                    if (screen != null) {
                        String className = screen.getClass().getName();
                        for (var entry : customStates.entrySet()) {
                            if (className.startsWith(entry.getKey())) {
                                rpc.setState(entry.getValue());
                                setState = true;
                                break;
                            }
                        }
                    }
                    if (!setState) rpc.setState("In main menu");
                }

                update = true;
            }
        }

        // Update
        if (update) DiscordIPC.setActivity(rpc);
        forceUpdate = false;
        lastWasInMainMenu = !Utils.canUpdate();
    }

    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (!Utils.canUpdate()) lastWasInMainMenu = false;
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WButton help = theme.button("Meteor Placeholders");
        help.action = () -> Util.getPlatform().openUri("https://github.com/MeteorDevelopment/meteor-client/wiki/Starscript");

        return help;
    }

    private static String sanitizeRpcText(String text) {
        if (text == null || text.isBlank()) return null;
        if (xyz.thm.addon.utils.PrivacyGuard.containsSecrets(text)) return null;
        if (!xyz.thm.addon.utils.PrivacyGuard.allowsRemoteExport()
            && xyz.thm.addon.utils.PrivacyText.containsCoordinates(text)) {
            return null;
        }
        return text;
    }

    private static boolean templateCanLeakCoordinates(String template) {
        return xyz.thm.addon.utils.PrivacyText.templateCanLeakCoordinates(template);
    }

    public enum SelectMode {
        Random,
        Sequential
    }
}
