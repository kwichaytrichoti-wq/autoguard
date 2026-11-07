package dev.autoguard.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.client.KeyMapping;

/** Exposes KeyMapping's private clickCount so AutoGuard can detect a pending attack click
 *  without consuming it (vanilla's consumeClick() would steal the click). */
@Mixin(KeyMapping.class)
public interface KeyMappingClickAccessor {
    @Accessor("clickCount")
    int autoguard$getClickCount();
}
