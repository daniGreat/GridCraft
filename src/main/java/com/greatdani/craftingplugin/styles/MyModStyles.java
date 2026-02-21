package com.greatdani.craftingplugin.styles;

import au.ellie.hyui.builders.*;
import au.ellie.hyui.types.*;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;

import static au.ellie.hyui.types.DefaultStyles.*;

public final class MyModStyles {
    private MyModStyles() {}

    // Color palette
    private static final String PRIMARY_COLOR = "#4a90e2";
    private static final String SUCCESS_COLOR = "#4caf50";
    private static final String DANGER_COLOR = "#666666";
    private static final String TEXT_COLOR = "#4caf50";
    private static final String DISABLED_COLOR = "#666666";

    // Texture paths
    private static final String BUTTON_DEFAULT = "Common/Buttons/Destructive.png";
    private static final String BUTTON_HOVER = "Common/Buttons/Destructive_Hovered.png";
    private static final String BUTTON_PRESSED = "Common/Buttons/Destructive_Pressed.png";

    private static final int HOTBAR_SIZE = 9; // or read from capacity
    public static Value<PatchStyle>[] HOTBAR_OVERLAYS;
    public static Value<PatchStyle> OUTLINE;
    public static Value<PatchStyle> OutputOutline;
    /**
     * Primary button style - for main actions (Save, Confirm, etc.)
     */
    public static TextButtonStyle primaryButton() {
        return new TextButtonStyle()
                .withDefault((TextButtonStyleState) new TextButtonStyleState()
                        .withBackground(new HyUIPatchStyle()
                                .setTexturePath(BUTTON_DEFAULT)
                                .setColor(PRIMARY_COLOR)
                                .setBorder(12))
                        .withLabelStyle(buttonLabel(TEXT_COLOR)))
                .withHovered((TextButtonStyleState) new TextButtonStyleState()
                        .withBackground(new HyUIPatchStyle()
                                .setTexturePath(BUTTON_HOVER)
                                .setColor(PRIMARY_COLOR)
                                .setBorder(12))
                        .withLabelStyle(buttonLabel(TEXT_COLOR)))
                .withPressed((TextButtonStyleState) new TextButtonStyleState()
                        .withBackground(new HyUIPatchStyle()
                                .setTexturePath(BUTTON_PRESSED)
                                .setColor(PRIMARY_COLOR)
                                .setBorder(12))
                        .withLabelStyle(buttonLabel(TEXT_COLOR)))
                .withDisabled((TextButtonStyleState) new TextButtonStyleState()
                        .withBackground(new HyUIPatchStyle()
                                .setTexturePath(BUTTON_DEFAULT)
                                .setColor(DISABLED_COLOR)
                                .setBorder(12))
                        .withLabelStyle(buttonLabel(DISABLED_COLOR)))
                .withSounds(buttonSounds());
    }

    /**
     * Success button style - for positive actions (Complete, Accept, etc.)
     */
    public static TextButtonStyle successButton() {
        return new TextButtonStyle()
                .withDefault((TextButtonStyleState) new TextButtonStyleState()
                        .withBackground(new HyUIPatchStyle()
                                .setTexturePath(BUTTON_DEFAULT)
                                .setColor(SUCCESS_COLOR)
                                .setBorder(12))
                        .withLabelStyle(buttonLabel(TEXT_COLOR)))
                .withHovered((TextButtonStyleState) new TextButtonStyleState()
                        .withBackground(new HyUIPatchStyle()
                                .setTexturePath(BUTTON_HOVER)
                                .setColor(SUCCESS_COLOR)
                                .setBorder(12))
                        .withLabelStyle(buttonLabel(TEXT_COLOR)))
                .withPressed((TextButtonStyleState) new TextButtonStyleState()
                        .withBackground(new HyUIPatchStyle()
                                .setTexturePath(BUTTON_PRESSED)
                                .setColor(SUCCESS_COLOR)
                                .setBorder(12))
                        .withLabelStyle(buttonLabel(TEXT_COLOR)))
                .withDisabled((TextButtonStyleState) new TextButtonStyleState()
                        .withBackground(new HyUIPatchStyle()
                                .setTexturePath(BUTTON_DEFAULT)
                                .setColor(DISABLED_COLOR)
                                .setBorder(12))
                        .withLabelStyle(buttonLabel(DISABLED_COLOR)))
                .withSounds(buttonSounds());
    }

    /**
     * Danger button style - for destructive actions (Delete, Cancel, etc.)
     */
    public static TextButtonStyle dangerButton() {
        return new TextButtonStyle()
                .withDefault((TextButtonStyleState) new TextButtonStyleState()
                        .withBackground(new HyUIPatchStyle()
                                .setTexturePath(BUTTON_DEFAULT)
                                .setColor(DANGER_COLOR)
                                .setBorder(12))
                        .withLabelStyle(buttonLabel(TEXT_COLOR)))
                .withHovered((TextButtonStyleState) new TextButtonStyleState()
                        .withBackground(new HyUIPatchStyle()
                                .setTexturePath(BUTTON_HOVER)
                                .setColor(DANGER_COLOR)
                                .setBorder(12))
                        .withLabelStyle(buttonLabel(TEXT_COLOR)))
                .withPressed((TextButtonStyleState) new TextButtonStyleState()
                        .withBackground(new HyUIPatchStyle()
                                .setTexturePath(BUTTON_PRESSED)
                                .setColor(DANGER_COLOR)
                                .setBorder(12))
                        .withLabelStyle(buttonLabel(TEXT_COLOR)))
                .withDisabled((TextButtonStyleState) new TextButtonStyleState()
                        .withBackground(new HyUIPatchStyle()
                                .setTexturePath(BUTTON_DEFAULT)
                                .setColor(DISABLED_COLOR)
                                .setBorder(12))
                        .withLabelStyle(buttonLabel(DISABLED_COLOR)))
                .withSounds(buttonDestructiveSounds());
    }

    /**
     * Helper: Button label style
     */
    private static HyUIStyle buttonLabel(String color) {
        return new HyUIStyle()
                .setTextColor(color)
                .setFontSize(50)
                .setRenderBold(true)
                .setRenderUppercase(true)
                .setHorizontalAlignment(Alignment.Center)
                .setVerticalAlignment(Alignment.Center);
    }

    /**
     * Panel background
     */
    public static HyUIPatchStyle panelBackground() {
        return new HyUIPatchStyle()
                .setTexturePath("MyMod/Panel.png")
                .setBorder(16);
    }

    /**
     * Tooltip style
     */
    public static TextTooltipStyle tooltip() {
        return new TextTooltipStyle()
                .withBackground(new HyUIPatchStyle()
                        .setTexturePath("MyMod/Tooltip.png")
                        .setBorder(24))
                .withMaxWidth(400)
                .withLabelStyle(new HyUIStyle()
                        .setWrap(true)
                        .setFontSize(14)
                        .setTextColor("#e0e0e0"))
                .withPadding(24);
    }


    public static void ensureOutline() {
        if (OUTLINE == null) OUTLINE = Value.of(makeOutline("Slot_Overlay_Outline.png"));
    }
    public static void ensureOutputOutline() {
        if (OutputOutline == null) OutputOutline = Value.of(makeOutline("Slot_OutputOverlay_Outline.png"));
    }

    public static void ensureHotbarOverlays() {
        if (HOTBAR_OVERLAYS == null) HOTBAR_OVERLAYS = buildHotbarOverlays();
    }

    @SuppressWarnings("unchecked")
    public static Value<PatchStyle>[] buildHotbarOverlays() {
        Value<PatchStyle>[] arr = new Value[HOTBAR_SIZE];
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            PatchStyle ps = new PatchStyle();
            ps.setTexturePath(Value.of("Slot_Overlay" + (i + 1) + ".png")); // 1-based filenames
            arr[i] = Value.of(ps);
        }
        return arr;
    }

    public static PatchStyle makeOutline(String path) {
        PatchStyle p = new PatchStyle();
        p.setTexturePath(Value.of(path));
        return p;
    }
    public static void applyCombinedInventoryStyles(ItemGridBuilder invGrid) {
        ensureOutline();
        ensureHotbarOverlays();

        // 1. Apply standard outline to Storage (Slots 0 through 35)
        for (int slot = 0; slot < 36; slot++) {
            ItemGridSlot s = invGrid.getSlot(slot);
            if (s != null) {
                s.setOverlay(OUTLINE);
            }
        }

        for (int i = 0; i < 9; i++) {
            int gridSlotIndex = 36 + i;
            ItemGridSlot s = invGrid.getSlot(gridSlotIndex);
            if (s != null) {
                s.setOverlay(HOTBAR_OVERLAYS[i]);
            }
        }
    }

    public static void applyHotbarSlotStyles(ItemGridBuilder hotbarGrid, int capacity) {
        ensureHotbarOverlays();
        for (int slot = 0; slot < capacity; slot++) {
            ItemGridSlot s = hotbarGrid.getSlot(slot);
            if (s == null) continue;

            s.setOverlay(HOTBAR_OVERLAYS[slot]);

        }
    }
    public static void applyInventorySlotStyles(ItemGridBuilder invGrid, int capacity) {
        ensureOutline();
        for (int slot = 0; slot < capacity; slot++) {
            ItemGridSlot s = invGrid.getSlot(slot);
            if (s == null) continue;

            s.setOverlay(OUTLINE);

        }
    }
    public static void applyOutputSlotStyles(ItemGridBuilder invGrid) {
        ensureOutputOutline();
        ItemGridSlot s = invGrid.getSlot(0);
        if (s == null) return;

        s.setOverlay(OutputOutline);
    }
}
