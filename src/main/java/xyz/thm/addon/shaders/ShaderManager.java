package xyz.thm.addon.shaders;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import xyz.thm.addon.THMAddon;
import xyz.thm.addon.system.THMSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Discovers the .fsh backgrounds shipped under assets/thm-addon/shaders and picks which
// one is currently active, per THMSystem's main-menu shader settings.
public class ShaderManager {
    private static final String BASE_PATH = "shaders";
    private static final Random RANDOM = new Random();
    // Screens don't always re-init when navigating back to them (e.g. cancelling out of the
    // multiplayer/singleplayer screen can reuse the same TitleScreen instance), which made
    // "random" look stuck on one shader. This is a time-based safety net on top of the
    // per-TitleScreen#init reroll, so it reliably varies regardless of screen reuse.
    private static final long REROLL_INTERVAL_MS = 20_000;

    private static List<String> cached;
    private static String activeRoll;
    private static long lastRollAt;

    public static List<String> availableShaders() {
        if (cached == null) {
            List<String> names = new ArrayList<>();
            try {
                MinecraftClient.getInstance().getResourceManager()
                    .findResources(BASE_PATH, id -> id.getNamespace().equals(THMAddon.MOD_ID) && id.getPath().endsWith(".fsh"))
                    .keySet()
                    .forEach(id -> names.add(id.getPath().substring(BASE_PATH.length() + 1, id.getPath().length() - ".fsh".length())));
            } catch (Exception e) {
                THMAddon.LOG.warn("[THM] Failed to list main-menu shaders", e);
            }
            names.sort(String::compareTo);
            cached = names;
        }
        return cached;
    }

    public static Identifier resourceId(String shaderName) {
        return Identifier.of(THMAddon.MOD_ID, BASE_PATH + "/" + shaderName + ".fsh");
    }

    /** Re-rolls which shader is active. Safe to call often - see REROLL_INTERVAL_MS. */
    public static void reroll() {
        THMSystem system = THMSystem.get();
        List<String> all = availableShaders();
        lastRollAt = System.currentTimeMillis();

        if (all.isEmpty()) {
            activeRoll = null;
            return;
        }

        if (!system.shaderRandom.get()) {
            String choice = system.shaderChoice.get();
            activeRoll = all.contains(choice) ? choice : null;
            return;
        }

        // Empty selection = no filter configured yet, allow the whole pool.
        java.util.Set<String> allowed = system.shaderPool.get().selected();
        List<String> pool = allowed.isEmpty() ? all : all.stream().filter(allowed::contains).toList();
        if (pool.isEmpty()) pool = all;

        // Avoid rolling the same shader twice in a row when there's more than one to pick from.
        String previous = activeRoll;
        String next = pool.get(RANDOM.nextInt(pool.size()));
        if (next.equals(previous) && pool.size() > 1) {
            List<String> withoutPrevious = pool.stream().filter(s -> !s.equals(previous)).toList();
            next = withoutPrevious.get(RANDOM.nextInt(withoutPrevious.size()));
        }
        activeRoll = next;
    }

    /** @return the currently active shader name, or null for the vanilla panorama. */
    public static String active() {
        if (activeRoll == null || System.currentTimeMillis() - lastRollAt > REROLL_INTERVAL_MS) {
            reroll();
        }
        return activeRoll;
    }
}
