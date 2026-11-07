package dev.autoguard.client;

import dev.autoguard.config.GuardConfig;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/** Compact settings UI sized for narrow launcher screens; advanced values are text fields. */
public final class GuardConfigScreen extends Screen {
    private final Screen parent;
    private final boolean advanced;
    private int columnWidth;
    private int left;
    private int right;
    private EditBox margin, arc, leadTicks, resumeDelay, maxReactionDistance, rangedReactionDistance, holdAfterThreatGone, arrival, durability;

    public GuardConfigScreen(Screen parent) {
        this(parent, false);
    }

    private GuardConfigScreen(Screen parent, boolean advanced) {
        super(Component.literal(advanced ? "AutoGuard advanced settings" : "AutoGuard settings"));
        this.parent = parent;
        this.advanced = advanced;
    }

    @Override
    protected void init() {
        GuardConfig c = AutoGuardClient.CONFIG;
        columnWidth = Math.max(70, (this.width - 20) / 2 - 4);
        left = this.width / 2 - columnWidth - 4;
        right = this.width / 2 + 4;
        if (advanced) initAdvanced(c); else initToggles(c);
    }

    private void initToggles(GuardConfig c) {
        int y = 30;
        addRenderableWidget(button(left, y, "Preset: " + c.preset, b -> {
            c.preset = switch (c.preset) { case "Cautious" -> "Normal"; case "Normal" -> "Aggressive"; default -> "Cautious"; };
            b.setMessage(Component.literal("Preset: " + c.preset));
        }));
        toggle(right, y, "Remote server", () -> c.allowRemoteServers, value -> c.allowRemoteServers = value);
        y += 22;
        toggle(left, y, "Hostile mobs", () -> c.hostileMobs, value -> c.hostileMobs = value);
        toggle(right, y, "Angry neutral", () -> c.angryNeutralMobs, value -> c.angryNeutralMobs = value);
        y += 22;
        toggle(left, y, "Melee", () -> c.melee, value -> c.melee = value);
        toggle(right, y, "Projectiles", () -> c.projectiles, value -> c.projectiles = value);
        y += 22;
        toggle(left, y, "Creepers", () -> c.creepers, value -> c.creepers = value);
        toggle(right, y, "Creative", () -> c.creative, value -> c.creative = value);
        y += 22;
        toggle(left, y, "Respect item use", () -> c.dontInterruptItemUse, value -> c.dontInterruptItemUse = value);
        toggle(right, y, "Input priority", () -> c.playerInputPriority, value -> c.playerInputPriority = value);
        y += 22;
        toggle(left, y, "Show HUD", () -> c.showHud, value -> c.showHud = value);
        addRenderableWidget(button(right, y, "HUD: " + c.hudPosition, b -> {
            c.hudPosition = switch (c.hudPosition) { case "TOP_LEFT" -> "TOP_RIGHT"; case "TOP_RIGHT" -> "BOTTOM_LEFT"; case "BOTTOM_LEFT" -> "BOTTOM_RIGHT"; default -> "TOP_LEFT"; };
            b.setMessage(Component.literal("HUD: " + c.hudPosition));
        }));
        addRenderableWidget(button(left, this.height - 28, "Advanced values", b -> this.minecraft.gui.setScreen(new GuardConfigScreen(parent, true))));
        addRenderableWidget(button(right, this.height - 28, "Done", b -> onClose()));
    }

    private void initAdvanced(GuardConfig c) {
        int firstY = Math.max(24, this.height / 2 - 90);
        margin = number(left, firstY, "Early margin (blocks)", Double.toString(c.customEarlyMargin));
        arc = number(right, firstY, "Front arc (degrees)", Double.toString(c.customArcDegrees));
        leadTicks = number(left, firstY + 30, "Lead ticks", Double.toString(c.customLeadTicks));
        resumeDelay = number(right, firstY + 30, "Resume delay (s)", Double.toString(c.customResumeDelaySeconds));
        maxReactionDistance = number(left, firstY + 60, "Max reaction dist (blocks)", Double.toString(c.customMaxReactionDistance));
        rangedReactionDistance = number(right, firstY + 60, "Ranged reaction dist (blocks)", Double.toString(c.customRangedReactionDistance));
        holdAfterThreatGone = number(left, firstY + 90, "Hold after threat gone (s)", Double.toString(c.customHoldAfterThreatGoneSeconds));
        arrival = number(right, firstY + 90, "Arrival (ticks)", Double.toString(c.projectileArrivalTicks));
        durability = number(left, firstY + 120, "Minimum durability", Integer.toString(c.minimumDurability));
        addRenderableWidget(button(left, this.height - 28, "Back", b -> {
            saveValues();
            this.minecraft.gui.setScreen(new GuardConfigScreen(parent, false));
        }));
        addRenderableWidget(button(right, this.height - 28, "Done", b -> onClose()));
    }

    private void toggle(int x, int y, String name, java.util.function.BooleanSupplier getter, Consumer<Boolean> setter) {
        addRenderableWidget(button(x, y, name + ": " + on(getter.getAsBoolean()), b -> {
            boolean next = !getter.getAsBoolean();
            setter.accept(next);
            b.setMessage(Component.literal(name + ": " + on(next)));
        }));
    }

    private EditBox number(int x, int y, String label, String value) {
        EditBox box = new EditBox(font, x, y + 12, columnWidth, 20, Component.literal(label));
        box.setHint(Component.literal(label));
        box.setValue(value);
        // 26.3: EditBox.setFilter is gone; numeric parsing errors fall back to the previous value.
        addRenderableWidget(box);
        return box;
    }

    private Button button(int x, int y, String text, Consumer<Button> action) {
        return Button.builder(Component.literal(text), button -> action.accept(button))
                .bounds(x, y, columnWidth, 20).build();
    }

    private static String on(boolean value) { return value ? "ON" : "OFF"; }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        graphics.centeredText(font, getTitle(), width / 2, 12, 0xFFFFFFFF);
        if (advanced) {
            int y = Math.max(18, height / 2 - 112);
            graphics.text(font, "Early margin (blocks)", left, y, 0xFFFFFFFF);
            graphics.text(font, "Front arc (degrees)", right, y, 0xFFFFFFFF);
            graphics.text(font, "Lead ticks", left, y + 30, 0xFFFFFFFF);
            graphics.text(font, "Resume delay (s)", right, y + 30, 0xFFFFFFFF);
            graphics.text(font, "Max reaction dist (blocks)", left, y + 60, 0xFFFFFFFF);
            graphics.text(font, "Ranged reaction dist (blocks)", right, y + 60, 0xFFFFFFFF);
            graphics.text(font, "Hold after threat gone (s)", left, y + 90, 0xFFFFFFFF);
            graphics.text(font, "Projectile arrival (ticks)", right, y + 90, 0xFFFFFFFF);
            graphics.text(font, "Minimum durability", left, y + 120, 0xFFFFFFFF);
        }
    }

    @Override
    public void onClose() {
        saveValues();
        if (minecraft != null) minecraft.gui.setScreen(parent);
    }

    private void saveValues() {
        if (!advanced || margin == null) {
            AutoGuardClient.CONFIG.sanitize();
            AutoGuardClient.CONFIG.save();
            return;
        }
        GuardConfig c = AutoGuardClient.CONFIG;
        c.customEarlyMargin = parse(margin, c.customEarlyMargin);
        c.customArcDegrees = parse(arc, c.customArcDegrees);
        c.customLeadTicks = parse(leadTicks, c.customLeadTicks);
        c.customResumeDelaySeconds = parse(resumeDelay, c.customResumeDelaySeconds);
        c.customMaxReactionDistance = parse(maxReactionDistance, c.customMaxReactionDistance);
        c.customRangedReactionDistance = parse(rangedReactionDistance, c.customRangedReactionDistance);
        c.customHoldAfterThreatGoneSeconds = parse(holdAfterThreatGone, c.customHoldAfterThreatGoneSeconds);
        c.projectileArrivalTicks = parse(arrival, c.projectileArrivalTicks);
        c.minimumDurability = (int) parse(durability, c.minimumDurability);
        c.sanitize();
        c.save();
    }

    private static double parse(EditBox box, double fallback) {
        try { return Double.parseDouble(box.getValue()); } catch (RuntimeException ignored) { return fallback; }
    }
}
