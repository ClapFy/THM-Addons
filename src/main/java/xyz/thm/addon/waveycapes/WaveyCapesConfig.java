package xyz.thm.addon.waveycapes;

import xyz.thm.addon.system.THMSystem;

public class WaveyCapesConfig {
    public static boolean enabled = false;
    public static CapeStyle capeStyle = CapeStyle.SMOOTH;
    public static WindMode windMode = WindMode.NONE;
    public static float gravity = 25;
    public static float heightMultiplier = 6;
    public static float straveMultiplier = 2;

    public static void syncFromSystem() {
        THMSystem sys = THMSystem.get();
        if (sys == null) return;
        enabled = sys.wavyCapes.get();
        capeStyle = sys.wavyCapeStyle.get();
        windMode = sys.wavyWindMode.get();
        gravity = sys.wavyGravity.get().floatValue();
        heightMultiplier = sys.wavyHeightMultiplier.get().floatValue();
        straveMultiplier = sys.wavyStraveMultiplier.get().floatValue();
    }
}
