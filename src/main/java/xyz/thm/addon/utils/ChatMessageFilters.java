package xyz.thm.addon.utils;

import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import net.minecraft.text.Text;

import java.util.Locale;

public final class ChatMessageFilters {
    private static final int MAX_DETAIL_LENGTH = 160;
    private static final String KITBOT_UNABLE_TO_FULFILL_TRIGGER = "Unable to fulfill order. Report this issue";
    private static final String KITBOT_INSUFFICIENT_TOKENS_TRIGGER = "Order cancelled: Insufficient tokens.";
    private static final String KITBOT_TEMPORARY_ERROR_TRIGGER = "An error occurred. Please try again later.";
    private static final String KITBOT_PLAYER_NOT_FOUND_TRIGGER = "Player not found!";
    private static final String KITBOT_MAX_ORDER_TRIGGER = "Maximum 16 kits total per order";
    private static final String KITBOT_INBOUND_TRIGGER = "Bot has your kits, sending a teleport";
    private static final String KITBOT_TELEPORT_TIMEOUT_TRIGGER = "Oops, looks like you didnt accept your teleport in time";

    private ChatMessageFilters() {
    }

    public static boolean isLocalAddonOutput(ReceiveMessageEvent event) {
        if (event == null) return false;
        Text message = event.getMessage();
        return message != null && isLocalAddonOutput(message.getString());
    }

    public static boolean isLocalAddonOutput(String message) {
        if (message == null) return false;

        String trimmed = message.stripLeading();
        if (trimmed.isEmpty()) return false;

        String lower = trimmed.toLowerCase(Locale.ROOT);
        return lower.startsWith("[meteor]")
            || lower.startsWith("[thm]")
            || lower.startsWith("[kitbot frontend]")
            || lower.startsWith("[thm highwaybuilder]")
            || lower.startsWith("[thm hwy monitor]")
            || lower.startsWith("[obsidian farmer thm]")
            || lower.startsWith("[elytra route]");
    }

    public static String compactDetail(String detail) {
        if (detail == null || detail.isBlank()) return "no detail";

        String compact = detail.replaceAll("\\s+", " ").trim();
        if (isLocalAddonOutput(compact)) return "local addon output ignored";

        compact = compact
            .replace(KITBOT_UNABLE_TO_FULFILL_TRIGGER, "Unable to fulfill order")
            .replace(KITBOT_INSUFFICIENT_TOKENS_TRIGGER, "Insufficient tokens")
            .replace(KITBOT_TEMPORARY_ERROR_TRIGGER, "Temporary KitBot error")
            .replace(KITBOT_PLAYER_NOT_FOUND_TRIGGER, "Player not found")
            .replace(KITBOT_MAX_ORDER_TRIGGER, "Maximum kit order exceeded")
            .replace(KITBOT_INBOUND_TRIGGER, "KitBot delivery inbound")
            .replace(KITBOT_TELEPORT_TIMEOUT_TRIGGER, "KitBot teleport acceptance timed out");

        if (compact.length() <= MAX_DETAIL_LENGTH) return compact;
        return compact.substring(0, MAX_DETAIL_LENGTH - 3) + "...";
    }
}
