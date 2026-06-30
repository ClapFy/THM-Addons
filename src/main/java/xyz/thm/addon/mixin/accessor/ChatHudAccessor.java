package xyz.thm.addon.mixin.accessor;

import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChatHud.class)
public interface ChatHudAccessor {
    @Invoker("addMessage")
    void thm$addMessage(ChatHudLine line);
}
