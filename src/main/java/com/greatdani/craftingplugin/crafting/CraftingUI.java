package com.greatdani.craftingplugin.crafting;

import au.ellie.hyui.builders.*;
import au.ellie.hyui.events.*;
import au.ellie.hyui.types.*;
import com.greatdani.craftingplugin.ResourceTypePreviewCache;
import com.greatdani.craftingplugin.styles.MyModStyles;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.auth.PlayerAuthentication;
import com.hypixel.hytale.server.core.entity.ItemUtils;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;
import com.hypixel.hytale.server.core.ui.LocalizableString;
import com.hypixel.hytale.server.core.ui.PatchStyle;
import com.hypixel.hytale.server.core.ui.Value;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.greatdani.craftingplugin.crafting.RecipeRegistry.prettyItemId;

public class CraftingUI {

    private static final Map<String, CraftingRecipe> currentRecipes = new HashMap<>();
    private static final Map<String, String> selectedRecipeId = new HashMap<>();

    private static final int SALVAGE_SECTION_ID = 10;
    private static final int STORAGE_SECTION_ID = 30;
    private static final int OUTPUT_SECTION_ID = 20;
    private static final int HOTBAR_SECTION_ID = 50;
    private static final int PREVIEW_SECTION_ID = 60;
    private static final int PREVIEW_OUTPUT_SECTION_ID = 70;

    private static boolean isResource;


    public static void open(PlayerRef playerRef, Store<EntityStore> store, Player playerComponent, PlayerAuthentication playerAuthentication) {
        String uuid = playerAuthentication.getUuid().toString();

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .loadHtml("Pages/GridCraft.html");

        HyUIPatchStyle defaultBg = new HyUIPatchStyle()
                .setTexturePath("ContainerCloseButton.png");

        HyUIPatchStyle hoveredBg = new HyUIPatchStyle()
                .setTexturePath("ContainerCloseButtonHovered.png");

        HyUIPatchStyle pressedBg = new HyUIPatchStyle()
                .setTexturePath("ContainerCloseButtonPressed.png");

        HyUIPatchStyle disabledBg = new HyUIPatchStyle();

        HyUIStyle defaultLabel = new HyUIStyle()
//                .setTextColor("#ffff00")
//                .setFontSize(35)
//                .setOutlineColor("#ffff00")
//                .setRenderBold(true)
                .setHorizontalAlignment(Alignment.Center)
                .setVerticalAlignment(Alignment.Center);
        HyUIStyle pressedLabel = new HyUIStyle()
//                .setTextColor("#661404")
//                .setFontSize(24)
//                .setOutlineColor("#ffff00")
//                .setRenderBold(true)
                .setHorizontalAlignment(Alignment.Center)
                .setVerticalAlignment(Alignment.Center);

        HyUIStyle hoveredLabel = new HyUIStyle()
//                .setTextColor("#888888")  // Yellow on hover
//                .setFontSize(16)
//                .setOutlineColor("#ffff00")
//                .setRenderBold(true)
                .setHorizontalAlignment(Alignment.Center)
                .setVerticalAlignment(Alignment.Center);

        HyUIStyle disabledLabel = new HyUIStyle()
//                .setTextColor("#888888")  // Gray when disabled
//                .setFontSize(16)
//                .setRenderBold(true)
                .setHorizontalAlignment(Alignment.Center)
                .setVerticalAlignment(Alignment.Center);

// Create text button style
        ButtonStyle customButtonStyle = new ButtonStyle()
                .withDefault(new ButtonStyleState()
                        .withBackground(defaultBg)
                        .withLabelStyle(defaultLabel))
                .withHovered(new ButtonStyleState()
                        .withBackground(hoveredBg)
                        .withLabelStyle(hoveredLabel))
                .withPressed(new ButtonStyleState()
                        .withBackground(pressedBg)
                        .withLabelStyle(pressedLabel))  // Same as default
                .withDisabled(new ButtonStyleState()
                        .withBackground(disabledBg)
                        .withLabelStyle(disabledLabel))
                .withSounds(DefaultStyles.buttonSounds());

        page.getById("my-button-uttn", ButtonBuilder.class).ifPresent(button -> {

            button.withText("");
            button.withStyle(customButtonStyle);
        });

        page.getById("terminal-view", ItemGridBuilder.class).ifPresent(grid -> {
            grid.withInventorySectionId(PREVIEW_SECTION_ID);
            grid.withAreItemsDraggable(false);
            // create 9 slots ONCE
            int have = (grid.getSlots() == null) ? 0 : grid.getSlots().size();
            for (int i = have; i < 9; i++) {
                grid.addSlot(new ItemGridSlot());
            }
            System.out.println("terminal-view slotCount=" + (grid.getSlots()==null ? -1 : grid.getSlots().size()));

        });
        page.getById("output-terminal-view", ItemGridBuilder.class).ifPresent(grid -> {
            grid.withInventorySectionId(PREVIEW_OUTPUT_SECTION_ID);
            grid.withAreItemsDraggable(false);
            // create 1 slots ONCE
            if (grid.getSlots() == null || grid.getSlots().isEmpty()) {
                grid.addSlot(new ItemGridSlot());
            }

            System.out.println("output-terminal-view slotCount=" + (grid.getSlots()==null ? -1 : grid.getSlots().size()));

        });

        page.getById("Recipe", DropdownBoxBuilder.class).ifPresent(dropdown -> {

            HyUIStyle style = new HyUIStyle();

            for (CraftingRecipe r : RecipeRegistry.getAllRecipes()) {
                String id = r.getId();
                if (id == null || id.isBlank()) continue;

                String label = r.getLabel();
                if (label == null || label.isBlank()) label = id;

                dropdown.addEntry(id, label); //
            }
            dropdown.addEventListener(CustomUIEventBindingType.ValueChanged, (selectedId, ctx) -> {
                if (selectedId == null || selectedId.isBlank()) return;
                selectedRecipeId.put(uuid, selectedId);


                // store selected recipe per player
                CraftingRecipe r = RecipeRegistry.getById(selectedId);
                if (r == null) return;

                updateRecipePreviewGrid(ctx, r); // <-- this fills terminal-view
                updateRecipeOutputPreview(ctx, r);       // fills 1x1 output
                ctx.updatePage(false);
            });

            //dropdown.withShowSearchInput(true);
            dropdown.withStyle(style);
        });

        page.getById("my-label", LabelBuilder.class).ifPresent(containerBuilder -> {
            HyUIStyle style = new HyUIStyle();
            style.setFontSize(20);
            style.setTextColor("#FFFF00");
            style.setAlignment(Alignment.Center);
            style.setRenderBold(true);

            HyUIPadding padding = new HyUIPadding();
            padding.setBottom(10);
            padding.setTop(10);
            containerBuilder.withText("Crafting Table").withPadding(padding).withStyle(style);
        });
        page.getById("my-recipe-label", LabelBuilder.class).ifPresent(containerBuilder -> {
            HyUIStyle style = new HyUIStyle();
            style.setFontSize(20);
            style.setTextColor("#FFFF00");
            style.setAlignment(Alignment.Center);
            style.setRenderBold(true);

            HyUIPadding padding = new HyUIPadding();
            padding.setBottom(10);
            padding.setTop(10);
            containerBuilder.withText("Recipes").withPadding(padding).withStyle(style);
        });

        page.getById("inventory-label", LabelBuilder.class).ifPresent(containerBuilder -> {
            HyUIStyle style = new HyUIStyle();
            style.setFontSize(18);
            style.setTextColor("#FFFF00");
            style.setAlignment(Alignment.Center);
            style.setRenderBold(true);

            HyUIPadding padding = new HyUIPadding();
            padding.setBottom(10);
            padding.setTop(10);
            containerBuilder.withText("Inventory and Hotbar").withPadding(padding).withStyle(style);
        });
        page.getById("crafting-label", LabelBuilder.class).ifPresent(containerBuilder -> {
            HyUIStyle style = new HyUIStyle();
            style.setFontSize(18);
            style.setTextColor("#FFFF00");
            style.setAlignment(Alignment.Center);
            style.setRenderBold(true);

            HyUIPadding padding = new HyUIPadding();
            padding.setBottom(10);
            padding.setTop(10);
            containerBuilder.withText("Crafting Area").withPadding(padding).withStyle(style);
        });
//        page.getById("my_slot", ItemSlotBuilder.class).ifPresent(gridBuilder -> {
//
//            System.out.println("SLot 0 Found");
//            gridBuilder.withOutlineColor("#888888");
//            gridBuilder.withOutlineSize(10);
//            gridBuilder.withBackground("#888888");
//
//            //gridBuilder.withStyle(style);
//        });

        page.addEventListener("my-button-uttn", CustomUIEventBindingType.Activating,
                (data, ctx) -> {


                    ctx.getPage().ifPresent(pages -> pages.close());
                    playerRef.sendMessage(Message.raw("Exited out of crafting table!"));
//                    var labelText = ctx.getValueAs("my-label", String.class).orElse("N/A");
//                    ctx.getById("my-label", LabelBuilder.class).ifPresent(labelBuilder -> {
//                        labelBuilder.withText("Woah, I changed!");
//                        ctx.updatePage(true);
//                    });
                });
        page.onDismiss((ctx, wasForceClosed) -> {
            currentRecipes.remove(uuid);
            dragStates.remove(uuid);
            ItemContainer storage = playerComponent.getInventory().getStorage();
            int capacity = storage.getCapacity();

            ctx.getById("storage-grid", ItemGridBuilder.class).ifPresent(storageGrid -> {
                for (int slot = 0; slot < capacity; slot++) {
                    ItemGridSlot gridSlot = storageGrid.getSlot(slot);
                    ItemStack stack = gridSlot != null ? ItemGridBuilder.getItemStack(gridSlot) : null;
                    storage.setItemStackForSlot((short)slot, stack);
                }
            });

            ctx.getById("hotbar-grid", ItemGridBuilder.class).ifPresent(hotbarGrid -> {
                ItemContainer hotbar = playerComponent.getInventory().getHotbar();
                int cap = hotbar.getCapacity();

                for (int slot = 0; slot < cap; slot++) {
                    ItemGridSlot gridSlot = hotbarGrid.getSlot(slot);
                    ItemStack stack = gridSlot != null ? ItemGridBuilder.getItemStack(gridSlot) : null;
                    hotbar.setItemStackForSlot((short) slot, stack);
                }
            });

            ctx.getById("salvage-grid", ItemGridBuilder.class).ifPresent(salvageGrid -> {
                for (int i = 0; i < salvageGrid.getSlots().size(); i++) {
                    ItemGridSlot gridSlot = salvageGrid.getSlot(i);
                    ItemStack stack = gridSlot != null ? ItemGridBuilder.getItemStack(gridSlot) : null;
                    if (stack == null) continue;

                    // Try to find a free slot in inventory
                    AtomicBoolean placed = new AtomicBoolean(false);
                    for (short slot = 0; slot < capacity; slot++) {
                        ItemStack existing = storage.getItemStack(slot);
                        if (existing == null) {
                            storage.setItemStackForSlot(slot, stack);
                            placed.set(true);
                            break;
                        }
                        // Optionally merge with existing matching stacks
                        if (existing.getItem().getId().equals(stack.getItem().getId())) {
                            int maxStack = existing.getItem().getMaxStack();
                            int total = existing.getQuantity() + stack.getQuantity();
                            if (total <= maxStack) {
                                storage.setItemStackForSlot(slot, new ItemStack(stack.getItem().getId(), total));
                                placed.set(true);
                                break;
                            } else {
                                // Fill this slot and continue with remainder
                                storage.setItemStackForSlot(slot, new ItemStack(stack.getItem().getId(), maxStack));
                                stack = new ItemStack(stack.getItem().getId(), total - maxStack);
                            }
                        }
                    }

                    if (!placed.get()) {
                        // Inventory full — drop item on ground or handle however you want
                        ItemContainer hotbar = playerComponent.getInventory().getHotbar();
                        int hotbarCap = hotbar.getCapacity();
                        // same merge/place logic but against hotbar
                        for (short hSlot = 0; hSlot < hotbarCap; hSlot++) {
                            ItemStack hExisting = hotbar.getItemStack(hSlot);
                            if (hExisting == null) {
                                hotbar.setItemStackForSlot(hSlot, stack);
                                placed.set(true);
                                break;
                            }
                            // merge with matching stacks
                            if (hExisting.getItem().getId().equals(stack.getItem().getId())) {
                                int maxStack = hExisting.getItem().getMaxStack();
                                int total = hExisting.getQuantity() + stack.getQuantity();
                                if (total <= maxStack) {
                                    hotbar.setItemStackForSlot(hSlot, new ItemStack(stack.getItem().getId(), total));
                                    placed.set(true);
                                    break;
                                }
                            }
                        }

                        if (!placed.get()) {
                            ItemUtils.dropItem(playerRef.getReference(), stack, store);
                        }
                    }
                }
            });
        });
        page.getById("output-grid", ItemGridBuilder.class).ifPresent(grid -> {
            grid.withInventorySectionId(OUTPUT_SECTION_ID);
            grid.withAllowMaxStackDraggableItems(false);
            grid.withAreItemsDraggable(true);

            //grid.onSlotDoubleClicking(() -> {});

        });

        page.addEventListener("output-grid", CustomUIEventBindingType.Dropped, DroppedEventData.class, (drop, ctx) -> {

        });

        page.addEventListener("salvage-grid", CustomUIEventBindingType.Dropped, DroppedEventData.class, (drop, ctx) -> {

            if (drop.getSlotIndex() == null) {
                restoreDrag(uuid, SALVAGE_SECTION_ID, STORAGE_SECTION_ID,HOTBAR_SECTION_ID,  ctx);
                ctx.updatePage(false);
                return;
            }
            ctx.getById("salvage-grid", ItemGridBuilder.class).ifPresent(salvageGrid -> {
                handleDrop(drop, salvageGrid, SALVAGE_SECTION_ID, SALVAGE_SECTION_ID, STORAGE_SECTION_ID, HOTBAR_SECTION_ID, ctx);
                int cap = salvageGrid.getSlots().size();
                MyModStyles.applyInventorySlotStyles(salvageGrid, cap);
            });
            ctx.getById("hotbar-grid", ItemGridBuilder.class).ifPresent(hotbarGrid -> {
                int hotbarCap = playerComponent.getInventory().getHotbar().getCapacity();
                MyModStyles.applyHotbarSlotStyles(hotbarGrid, hotbarCap);
            });
            ctx.getById("storage-grid", ItemGridBuilder.class).ifPresent(storageGrid -> {
                int invCap = playerComponent.getInventory().getStorage().getCapacity();
                MyModStyles.applyInventorySlotStyles(storageGrid, invCap);
            });
            checkRecipes(uuid, ctx);
            ctx.updatePage(false);
        });

        page.addEventListener("salvage-grid", CustomUIEventBindingType.SlotClickPressWhileDragging,
                SlotClickPressWhileDraggingEventData.class, (e, ctx) -> {
                    handleSplitSafe(uuid, e, SALVAGE_SECTION_ID, SALVAGE_SECTION_ID, STORAGE_SECTION_ID,playerComponent, ctx);
                    ctx.updatePage(false);
                });

        page.addEventListener("storage-grid", CustomUIEventBindingType.SlotClickPressWhileDragging,
                SlotClickPressWhileDraggingEventData.class, (e, ctx) -> {
                    handleSplitSafe(uuid, e, STORAGE_SECTION_ID, SALVAGE_SECTION_ID, STORAGE_SECTION_ID, playerComponent, ctx);
                    ctx.updatePage(false);
                });
        page.addEventListener("output-grid", CustomUIEventBindingType.SlotClicking, SlotClickingEventData.class, (click, ctx) -> {
            ctx.getById("output-grid", ItemGridBuilder.class).ifPresent(outputGrid -> {
                ItemGridSlot outputSlot = outputGrid.getSlot(0);
                ItemStack outputStack = outputSlot != null ? ItemGridBuilder.getItemStack(outputSlot) : null;
                if (outputStack == null) return;


                ctx.getById("storage-grid", ItemGridBuilder.class).ifPresent(storageGrid -> {
                    ItemContainer storage = playerComponent.getInventory().getStorage();
                    int capacity = storage.getCapacity();
                    String itemId = outputStack.getItem().getId();
                    int qty = outputStack.getQuantity();
                    boolean placed = false;

                    // Try to merge with existing stacks first
                    for (short slot = 0; slot < capacity; slot++) {
                        ItemGridSlot existingSlot = storageGrid.getSlot((int)slot);
                        ItemStack existing = existingSlot != null ? ItemGridBuilder.getItemStack(existingSlot) : null;
                        if (existing != null && existing.getItem().getId().equals(itemId)) {
                            int maxStack = existing.getItem().getMaxStack();
                            int total = existing.getQuantity() + qty;
                            if (total <= maxStack) {
                                storageGrid.updateSlot(new ItemGridSlot(new ItemStack(itemId, total)), (int)slot);
                                placed = true;
                                break;
                            }
                        }
                    }

                    // If not merged, find empty slot
                    if (!placed) {
                        for (short slot = 0; slot < capacity; slot++) {
                            ItemGridSlot existingSlot = storageGrid.getSlot((int)slot);
                            ItemStack existing = existingSlot != null ? ItemGridBuilder.getItemStack(existingSlot) : null;
                            if (existing == null) {
                                storageGrid.updateSlot(new ItemGridSlot(new ItemStack(itemId, qty)), (int)slot);
                                placed = true;
                                break;
                            }
                        }
                    }
                    CraftingRecipe recipe = getRecipe(uuid);
                    if (placed && recipe != null) {

                        consumeIngredients(ctx, recipe);
                        checkRecipes(uuid, ctx);
                    }
                });
            });
            ctx.getById("storage-grid", ItemGridBuilder.class).ifPresent(storageGrid -> {
                int cap = playerComponent.getInventory().getStorage().getCapacity();
                MyModStyles.applyInventorySlotStyles(storageGrid, cap);
            });
            ctx.updatePage(false);
        });


        page.addEventListener("storage-grid", CustomUIEventBindingType.Dropped, DroppedEventData.class, (drop, ctx) -> {
            if (drop.getSlotIndex() == null) {
                restoreDrag(uuid, SALVAGE_SECTION_ID, STORAGE_SECTION_ID,HOTBAR_SECTION_ID,  ctx);
                ctx.updatePage(false);
                return;
            }

            if (drop.getSourceInventorySectionId() != null && drop.getSourceInventorySectionId() == OUTPUT_SECTION_ID) {
                craftOutputIntoStorage(uuid, ctx, playerComponent);
                ctx.updatePage(false);
                return;
            }

            ctx.getById("storage-grid", ItemGridBuilder.class).ifPresent(storageGrid -> {
                handleDrop(drop, storageGrid, STORAGE_SECTION_ID, SALVAGE_SECTION_ID, STORAGE_SECTION_ID, HOTBAR_SECTION_ID, ctx);
                int invCap = playerComponent.getInventory().getStorage().getCapacity();
                MyModStyles.applyInventorySlotStyles(storageGrid, invCap);
            });
            ctx.getById("hotbar-grid", ItemGridBuilder.class).ifPresent(hotbarGrid -> {

                int hotbarCap = playerComponent.getInventory().getHotbar().getCapacity();
                MyModStyles.applyHotbarSlotStyles(hotbarGrid, hotbarCap);
            });
            ctx.getById("salvage-grid", ItemGridBuilder.class).ifPresent(salvageGrid -> {
                int cap = salvageGrid.getSlots().size();
                MyModStyles.applyInventorySlotStyles(salvageGrid, cap); // if you want same outline there too
            });


            checkRecipes(uuid, ctx);
            ctx.updatePage(false);
        });
        page.addEventListener("hotbar-grid", CustomUIEventBindingType.Dropped, DroppedEventData.class, (drop, ctx) -> {
            if (drop.getSlotIndex() == null) {
                restoreDrag(uuid, SALVAGE_SECTION_ID, STORAGE_SECTION_ID,HOTBAR_SECTION_ID,  ctx);
                ctx.updatePage(false);
                return;
            }

            if (drop.getSourceInventorySectionId() != null && drop.getSourceInventorySectionId() == OUTPUT_SECTION_ID) {
                craftOutputIntoStorage(uuid, ctx, playerComponent);
                ctx.updatePage(false);
                return;
            }

            ctx.getById("hotbar-grid", ItemGridBuilder.class).ifPresent(storageGrid -> {
                handleDrop(drop, storageGrid, HOTBAR_SECTION_ID, SALVAGE_SECTION_ID, STORAGE_SECTION_ID, HOTBAR_SECTION_ID, ctx);
                // HOTBAR_OVERLAYS = buildHotbarOverlays();
                int cap = playerComponent.getInventory().getHotbar().getCapacity();
                MyModStyles.applyHotbarSlotStyles(storageGrid, cap);

            });
            ctx.getById("storage-grid", ItemGridBuilder.class).ifPresent(storageGrid -> {
                int invCap = playerComponent.getInventory().getStorage().getCapacity();
                MyModStyles.applyInventorySlotStyles(storageGrid, invCap);
            });
            ctx.getById("salvage-grid", ItemGridBuilder.class).ifPresent(salvageGrid -> {
                int cap = salvageGrid.getSlots().size();
                MyModStyles.applyInventorySlotStyles(salvageGrid, cap); // if you want same outline there too
            });

            checkRecipes(uuid, ctx);
            ctx.updatePage(false);
        });

// Set inventory section IDs HERE during setup, not in event handlers
        page.getById("salvage-grid", ItemGridBuilder.class).ifPresent(grid -> {
            grid.withInventorySectionId(SALVAGE_SECTION_ID);
            grid.withAllowMaxStackDraggableItems(false);
            int cap = grid.getSlots().size();
            MyModStyles.applyInventorySlotStyles(grid, cap);
            grid.onSlotDoubleClicking(() -> {

            });
        });
        page.getById("output-grid", ItemGridBuilder.class).ifPresent(MyModStyles::applyOutputSlotStyles);

        page.getById("storage-grid", ItemGridBuilder.class).ifPresent(grid -> {
            grid.withInventorySectionId(STORAGE_SECTION_ID);
            ItemContainer storage = playerComponent.getInventory().getStorage();
            int capacity = storage.getCapacity();

            for (short slot = 0; slot < capacity; ++slot) {
                ItemStack s = storage.getItemStack(slot);

                ItemGridSlot slotUi = (s != null) ? new ItemGridSlot(s) : new ItemGridSlot();

                grid.addSlot(slotUi);
            }
            MyModStyles.applyInventorySlotStyles(grid, capacity);

        });

        page.getById("hotbar-grid", ItemGridBuilder.class).ifPresent(grid -> {
            grid.withInventorySectionId(HOTBAR_SECTION_ID);
            ItemContainer storage = playerComponent.getInventory().getHotbar();
            int capacity = storage.getCapacity();

            for (int slot = 0; slot < capacity; ++slot) {

                ItemStack s = storage.getItemStack((short)slot);

                ItemGridSlot slotUi = (s != null) ? new ItemGridSlot(s) : new ItemGridSlot();

                grid.updateSlot(slotUi, slot);

            }
            MyModStyles.applyHotbarSlotStyles(grid, capacity);
        });

        page.addEventListener("salvage-grid", CustomUIEventBindingType.DragCancelled, DragCancelledEventData.class, (data, ctx) -> {
            restoreDrag(uuid, SALVAGE_SECTION_ID, STORAGE_SECTION_ID,HOTBAR_SECTION_ID,  ctx);
            ctx.updatePage(true);
        });

        page.addEventListener("storage-grid", CustomUIEventBindingType.DragCancelled, DragCancelledEventData.class, (data, ctx) -> {
            restoreDrag(uuid, SALVAGE_SECTION_ID, STORAGE_SECTION_ID,HOTBAR_SECTION_ID,  ctx);
            ctx.updatePage(true);
        });

        page.open(store);
    }

    private static void updateRecipeOutputPreview(UIContext ctx, CraftingRecipe recipe) {
        ctx.getById("output-terminal-view", ItemGridBuilder.class).ifPresent(grid -> {
            if (grid.getSlots() == null || grid.getSlots().isEmpty()) return;

            ItemStack res = (recipe != null) ? recipe.getResult() : null;
            grid.updateSlot(res == null ? new ItemGridSlot() : new ItemGridSlot(res), 0);
        });
    }

    private static ItemStack safePreviewStack(String id, int qty) {
        if (id == null || id.isBlank()) return null;
        try {
            ItemStack st = new ItemStack(id, Math.max(1, qty));
            if (st.getItem() == null) return null;
            String realId = st.getItem().getId();
            if (realId == null || realId.isBlank()) return null;
            return st;
        } catch (Exception ex) {
            return null;
        }
    }
    private static boolean hasSlot(ItemGridBuilder grid, int idx) {
        try {
            return grid.getSlot(idx) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static void updateRecipePreviewGrid(UIContext ctx, CraftingRecipe recipe) {
        ctx.getById("terminal-view", ItemGridBuilder.class).ifPresent(grid -> {

            if (grid.getSlots() == null || grid.getSlots().size() < 9) return;

            String[] pattern = recipe.getPattern();
            if (pattern == null || pattern.length < 3) return;

            // key = "I:Wood_Oak_Trunk" or "R:Wood_Trunk"
            Map<String, Integer> totals = new HashMap<>();

            for (int row = 0; row < 3; row++) {
                String line = (pattern[row] == null) ? "   " : pattern[row];
                while (line.length() < 3) line += " ";

                for (int col = 0; col < 3; col++) {
                    int idx = row * 3 + col;
                    if (grid.getSlot(idx) == null) return;

                    char ch = line.charAt(col);

                    if (ch == ' ') {
                        grid.updateSlot(new ItemGridSlot(), idx);
                        continue;
                    }

                    String required = recipe.getKey().get(ch);
                    int qty = recipe.getKeyQuantities().getOrDefault(ch, 1);

                    Boolean isRes = recipe.getKeyIsResourceType().get(ch);
                    ItemStack preview = safePreviewStack(required, qty);

                    boolean treatAsResourceType = Boolean.TRUE.equals(isRes) || preview == null;

                    if (treatAsResourceType) {
                        ItemStack rtPreview = ResourceTypePreviewCache.previewStack(required, qty);
                        if (rtPreview != null) preview = rtPreview;
                    }

                    grid.updateSlot(preview == null ? new ItemGridSlot() : new ItemGridSlot(preview), idx);

                    String key = (treatAsResourceType ? "R:" : "I:") + required;
                    totals.merge(key, qty, Integer::sum);
                }
            }

            // Build one compact text block
            StringBuilder out = new StringBuilder();

            for (var e : totals.entrySet()) {
                String k = e.getKey();
                int totalQty = e.getValue();

                boolean isResource = k.startsWith("R:");
                String id = isResource ? k.substring(2) : k;

                if (isResource) {
                    // Resource type label (not item id!)
                    String niceType = prettyItemId(id); // or your own prettyResourceType(id)
                    out.append("ResourceType: Any ")
                            .append(niceType)
                            .append(" x")
                            .append(totalQty)
                            .append("\n");
                } else {
                    // Exact item label
                    String niceName = getItemDisplayName(id);
                    out.append("RecipeRequires: ")
                            .append(niceName)
                            .append(" x")
                            .append(totalQty)
                            .append("\n");
                }
            }

            String finalText = out.toString().trim();

            ctx.getById("recipe-req-text", LabelBuilder.class).ifPresent(l -> {
                l.withStyle(new HyUIStyle().setTextColor("#4287f5").setAlignment(Alignment.Center));
                l.withText(finalText);
            });
        });
    }

    @Nonnull
    private static String getItemDisplayName(@Nonnull String itemId) {
        if (itemId.isBlank()) return itemId;

        Item item = (Item) Item.getAssetMap().getAsset(itemId);
        if (item == null || item == Item.UNKNOWN) {
            return prettyItemId(itemId); // your fallback
        }

        String translationKey = item.getTranslationKey();
        if (translationKey == null || translationKey.isBlank()) {
            return prettyItemId(itemId);
        }

        // If you want player's language later, replace "en-US" with that.
        String translated = I18nModule.get().getMessage("en-US", translationKey);

        if (translated != null && !translated.isBlank()) {
            return translated;
        }

        return prettyItemId(itemId);
    }


    private static CraftingRecipe getRecipe(String uuid) {
        return currentRecipes.get(uuid);
    }

    private static void checkRecipes(String uuid, UIContext ctx) {

        ctx.getById("salvage-grid", ItemGridBuilder.class).ifPresent(salvageGrid -> {
            CraftingRecipe recipe = RecipeRegistry.findMatch(salvageGrid);
            currentRecipes.put(uuid, recipe);

            ctx.getById("output-grid", ItemGridBuilder.class).ifPresent(outputGrid -> {
                outputGrid.updateSlot(recipe != null ? new ItemGridSlot(recipe.getResult()) : new ItemGridSlot(), 0);
                MyModStyles.applyOutputSlotStyles(outputGrid);
            });
            int cap = salvageGrid.getSlots().size();
            MyModStyles.applyInventorySlotStyles(salvageGrid, cap);
        });
    }

    private static void consumeIngredients(UIContext ctx, CraftingRecipe recipe) {
        ctx.getById("salvage-grid", ItemGridBuilder.class).ifPresent(salvageGrid -> {
            for (int i = 0; i < 9; i++) {
                char patternChar = recipe.getPatternChar(i);
                if (patternChar == ' ') continue;

                ItemGridSlot slot = salvageGrid.getSlot(i);
                ItemStack stack = slot != null ? ItemGridBuilder.getItemStack(slot) : null;
                if (stack == null) continue;

                int consume = recipe.getRequiredQuantity(patternChar);
                int newQty = stack.getQuantity() - consume;

                if (newQty > 0) {
                    salvageGrid.updateSlot(new ItemGridSlot(new ItemStack(stack.getItem().getId(), newQty)), i);
                } else {
                    salvageGrid.updateSlot(new ItemGridSlot(), i);
                }
            }
        });
    }


    private static void craftOutputIntoStorage(String uuid, UIContext ctx, Player playerComponent) {
        var outputGridOpt = ctx.getById("output-grid", ItemGridBuilder.class);
        var storageGridOpt = ctx.getById("storage-grid", ItemGridBuilder.class);
        if (outputGridOpt.isEmpty() || storageGridOpt.isEmpty()) return;

        ItemGridBuilder outputGrid = outputGridOpt.get();
        ItemGridBuilder storageGrid = storageGridOpt.get();

        ItemGridSlot outSlot = outputGrid.getSlot(0);
        ItemStack outputStack = outSlot != null ? ItemGridBuilder.getItemStack(outSlot) : null;
        if (outputStack == null) return;

        // reuse your existing merge logic (copy-paste from SlotClicking)
        String itemId = outputStack.getItem().getId();
        int qty = outputStack.getQuantity();

        ItemContainer storage = playerComponent.getInventory().getStorage();
        int capacity = storage.getCapacity();
        boolean placed = false;

        for (short slot = 0; slot < capacity; slot++) {
            ItemGridSlot existingSlot = storageGrid.getSlot((int) slot);
            ItemStack existing = existingSlot != null ? ItemGridBuilder.getItemStack(existingSlot) : null;
            if (existing != null && existing.getItem().getId().equals(itemId)) {
                int maxStack = existing.getItem().getMaxStack();
                int total = existing.getQuantity() + qty;
                if (total <= maxStack) {
                    storageGrid.updateSlot(new ItemGridSlot(new ItemStack(itemId, total)), (int) slot);
                    placed = true;
                    break;
                }
            }
        }

        if (!placed) {
            for (short slot = 0; slot < capacity; slot++) {
                ItemGridSlot existingSlot = storageGrid.getSlot((int) slot);
                ItemStack existing = existingSlot != null ? ItemGridBuilder.getItemStack(existingSlot) : null;
                if (existing == null) {
                    storageGrid.updateSlot(new ItemGridSlot(new ItemStack(itemId, qty)), (int) slot);
                    placed = true;
                    break;
                }
            }
        }
        CraftingRecipe recipe = getRecipe(uuid);
        if (placed  && recipe != null) {
            consumeIngredients(ctx, recipe);
            checkRecipes(uuid, ctx);
        }

        ctx.getById("storage-grid", ItemGridBuilder.class).ifPresent(grid -> {
            int cap = playerComponent.getInventory().getStorage().getCapacity();
            MyModStyles.applyInventorySlotStyles(storageGrid, cap);
        });
    }
    private static void handleOneSplit(
            String uuid,
            SlotClickPressWhileDraggingEventData e,
            int targetSectionId,
            int salvageSectionId,
            int storageSectionId,
            UIContext ctx
    ) {
        Integer mouseButton = e.getDragPressedMouseButton();
        Integer sourceIndex = e.getDragSourceSlotId();
        Integer targetIndex = e.getSlotIndex();
        Integer sourceSection = e.getDragSourceInventorySectionId();

        // Right-click only (mouse button 3). If you want half-split, change moveQty below.
        if (mouseButton == null || mouseButton != 3) return;

        if (sourceIndex == null || sourceSection == null) return;

        // Clicked outside UI / not on a slot => restore
        if (targetIndex == null || targetIndex < 0) {
            restoreDrag(uuid, salvageSectionId, storageSectionId, HOTBAR_SECTION_ID, ctx);
            return;
        }

        String targetGridId = (targetSectionId == storageSectionId) ? "storage-grid" : "salvage-grid";
        ItemGridBuilder targetGrid = ctx.getById(targetGridId, ItemGridBuilder.class).orElse(null);

        ItemGridBuilder sourceGrid =
                (sourceSection == salvageSectionId) ? ctx.getById("salvage-grid", ItemGridBuilder.class).orElse(null) :
                        (sourceSection == storageSectionId) ? ctx.getById("storage-grid", ItemGridBuilder.class).orElse(null) :
                                (sourceSection == targetSectionId) ? targetGrid :
                                        null;

        if (sourceGrid == null || targetGrid == null) {
            restoreDrag(uuid, salvageSectionId, storageSectionId, HOTBAR_SECTION_ID,  ctx);
            return;
        }

        // Same slot => ignore
        if (sourceGrid == targetGrid && sourceIndex.equals(targetIndex)) return;

        ItemGridSlot srcSlot = sourceGrid.getSlot(sourceIndex);
        ItemStack src = (srcSlot != null) ? ItemGridBuilder.getItemStack(srcSlot) : null;
        if (src == null) {
            restoreDrag(uuid, salvageSectionId, storageSectionId, HOTBAR_SECTION_ID, ctx);
            return;
        }

        beginDragIfNeeded(uuid, src, sourceIndex, sourceSection);

        int srcQty = src.getQuantity();
        if (srcQty < 2) {
            clearDrag(uuid);
            return;
        }

        String itemId = src.getItem().getId();
        int maxStack = src.getItem().getMaxStack();


        ItemGridSlot dstSlot = targetGrid.getSlot(targetIndex);
        ItemStack dst = (dstSlot != null) ? ItemGridBuilder.getItemStack(dstSlot) : null;
        int moveQty = 1;

        int actuallyMoved = 0;
        ItemStack newDst = null;

        if (dst == null) {
            actuallyMoved = moveQty;
            newDst = new ItemStack(itemId, actuallyMoved);
        } else if (dst.getItem().getId().equals(itemId)) {
            int space = maxStack - dst.getQuantity();
            if (space <= 0) {
                clearDrag(uuid);
                return; // full stack, nothing happens
            }
            actuallyMoved = Math.min(moveQty, space);
            newDst = new ItemStack(itemId, dst.getQuantity() + actuallyMoved);
        } else {
            clearDrag(uuid);
            return; // different item in target => do nothing
        }

        if (actuallyMoved <= 0) {
            clearDrag(uuid);
            return;
        }

        // Apply updates (target first, then source)
        targetGrid.updateSlot(new ItemGridSlot(newDst), targetIndex);

        int newSrcQty = srcQty - actuallyMoved;
        if (newSrcQty > 0) {
            sourceGrid.updateSlot(new ItemGridSlot(new ItemStack(itemId, newSrcQty)), sourceIndex);
        } else {
            sourceGrid.updateSlot(new ItemGridSlot(), sourceIndex);
        }

        clearDrag(uuid);
    }



    private static void handleSplitSafe(
            String uuid,
            SlotClickPressWhileDraggingEventData e,
            int targetSectionId,
            int salvageSectionId,
            int storageSectionId,
            Player playerComponent,
            UIContext ctx
    ) {
        Integer mouseButton = e.getDragPressedMouseButton();
        Integer sourceIndex = e.getDragSourceSlotId();
        Integer targetIndex = e.getSlotIndex();
        Integer sourceSection = e.getDragSourceInventorySectionId();


        // Right-click only (mouse button 3). If you want half-split, change moveQty below.
        if (mouseButton == null || mouseButton != 3) return;

        if (sourceIndex == null || sourceSection == null) return;

        // Clicked outside UI / not on a slot => restore
        if (targetIndex == null || targetIndex < 0) {
            restoreDrag(uuid, salvageSectionId, storageSectionId, HOTBAR_SECTION_ID, ctx);
            return;
        }

        String targetGridId = (targetSectionId == storageSectionId) ? "storage-grid" : "salvage-grid";
        ItemGridBuilder targetGrid = ctx.getById(targetGridId, ItemGridBuilder.class).orElse(null);

        ItemGridBuilder sourceGrid =
                (sourceSection == salvageSectionId) ? ctx.getById("salvage-grid", ItemGridBuilder.class).orElse(null) :
                        (sourceSection == storageSectionId) ? ctx.getById("storage-grid", ItemGridBuilder.class).orElse(null) :
                                (sourceSection == targetSectionId) ? targetGrid :
                                        null;

        if (sourceGrid == null || targetGrid == null) {
            restoreDrag(uuid, salvageSectionId, storageSectionId, HOTBAR_SECTION_ID,  ctx);
            return;
        }

        // Same slot => ignore
        if (sourceGrid == targetGrid && sourceIndex.equals(targetIndex)) return;

        ItemGridSlot srcSlot = sourceGrid.getSlot(sourceIndex);
        ItemStack src = (srcSlot != null) ? ItemGridBuilder.getItemStack(srcSlot) : null;
        if (src == null) {
            restoreDrag(uuid, salvageSectionId, storageSectionId, HOTBAR_SECTION_ID, ctx);
            return;
        }

        // Track original state so cancel/outside restores properly
        beginDragIfNeeded(uuid, src, sourceIndex, sourceSection);

        int srcQty = src.getQuantity();
        if (srcQty < 2) { // can't split 1 item
            clearDrag(uuid);
            return;
        }

        String itemId = src.getItem().getId();
        int maxStack = src.getItem().getMaxStack();

        // Right-click split amount:
        // If you want HALF split instead, use:
        // int moveQty = Math.max(1, srcQty / 2);

        ItemGridSlot dstSlot = targetGrid.getSlot(targetIndex);
        ItemStack dst = (dstSlot != null) ? ItemGridBuilder.getItemStack(dstSlot) : null;
        int moveQty = (dst == null) ? Math.max(1, srcQty / 2) : 1;

        int actuallyMoved = 0;
        ItemStack newDst = null;

        if (dst == null) {
            actuallyMoved = moveQty;
            newDst = new ItemStack(itemId, actuallyMoved);
        } else if (dst.getItem().getId().equals(itemId)) {
            int space = maxStack - dst.getQuantity();
            if (space <= 0) {
                clearDrag(uuid);
                return; // full stack, nothing happens
            }
            actuallyMoved = Math.min(moveQty, space);
            newDst = new ItemStack(itemId, dst.getQuantity() + actuallyMoved);
        } else {
            clearDrag(uuid);
            return; // different item in target => do nothing
        }

        if (actuallyMoved <= 0) {
            clearDrag(uuid);
            return;
        }

        // Apply updates (target first, then source)
        targetGrid.updateSlot(new ItemGridSlot(newDst), targetIndex);

        int newSrcQty = srcQty - actuallyMoved;
        if (newSrcQty > 0) {
            sourceGrid.updateSlot(new ItemGridSlot(new ItemStack(itemId, newSrcQty)), sourceIndex);
        } else {
            sourceGrid.updateSlot(new ItemGridSlot(), sourceIndex);
        }

        if (targetGridId.equals("salvage-grid")) {
            int cap = targetGrid.getSlots().size();
            MyModStyles.applyInventorySlotStyles(targetGrid, cap);
        }
        if (sourceGrid.equals("salvage-grid")) {
            int cap = sourceGrid.getSlots().size();
            MyModStyles.applyInventorySlotStyles(sourceGrid, cap);
        }

        ctx.getById("storage-grid", ItemGridBuilder.class).ifPresent(storageGrid -> {
            int invCap = playerComponent.getInventory().getStorage().getCapacity();
            MyModStyles.applyInventorySlotStyles(storageGrid, invCap);
        });

        clearDrag(uuid);
    }

    private static void handleDrop(DroppedEventData drop, ItemGridBuilder targetGrid,
                                   int targetSectionId, int salvageSectionId, int storageSectionId, int hotbarSectionId,
                                   UIContext ctx) {

        Integer sourceIndex = drop.getSourceSlotId();
        Integer targetIndex = drop.getSlotIndex();
        Integer sourceSection = drop.getSourceInventorySectionId();

        // Dropped outside UI => ignore (and let your DragCancelled restore handle it)
        if (targetIndex == null) return;

        // Allow left and right drop
        Integer mouseButton = drop.getPressedMouseButton();
        if (mouseButton != null && mouseButton == 3) {
            System.out.println("RIGHT CLICK");
            return; // right-click handled by SlotClickPressWhileDragging split logic
        }

        // Ignore same-slot drops
        if (sourceIndex != null && sourceSection != null
                && sourceSection == targetSectionId
                && sourceIndex.equals(targetIndex)) {
            return;
        }

        // Resolve source grid (so we can update remaining qty correctly)
        ItemGridBuilder sourceGrid = null;
        if (sourceIndex != null && sourceSection != null) {
            if (sourceSection == targetSectionId) {
                sourceGrid = targetGrid;
            } else if (sourceSection == salvageSectionId) {
                sourceGrid = ctx.getById("salvage-grid", ItemGridBuilder.class).orElse(null);
            } else if (sourceSection == storageSectionId) {
                sourceGrid = ctx.getById("storage-grid", ItemGridBuilder.class).orElse(null);
            }else if (sourceSection == hotbarSectionId) {
                sourceGrid = ctx.getById("hotbar-grid", ItemGridBuilder.class).orElse(null);
            }
        }

        // Read source stack (needed for partial moves like half-split)
        ItemStack sourceStack = null;
        int sourceQty = 0;
        if (sourceGrid != null && sourceIndex != null) {
            ItemGridSlot s = sourceGrid.getSlot(sourceIndex);
            sourceStack = s != null ? ItemGridBuilder.getItemStack(s) : null;
            if (sourceStack != null) {
                sourceQty = sourceStack.getQuantity();
            }
        }

        String itemId = drop.getItemStackId();
        int movedQty = drop.getItemStackQuantity();
        if (movedQty <= 0) return;

        // Clamp moved qty to source qty when we have a source slot
        if (sourceStack != null) {
            movedQty = Math.min(movedQty, sourceQty);
            if (movedQty <= 0) return;
        }

        ItemGridSlot existingSlot = targetGrid.getSlot(targetIndex);
        ItemStack existing = existingSlot != null ? ItemGridBuilder.getItemStack(existingSlot) : null;

        int placed = 0;
        int overflowBack = 0;

        if (existing == null) {
            // Empty target: place moved qty
            placed = movedQty;
            targetGrid.updateSlot(new ItemGridSlot(new ItemStack(itemId, placed)), targetIndex);

        } else if (existing.getItem().getId().equals(itemId)) {
            // Same item: merge with max stack
            int maxStack = existing.getItem().getMaxStack();
            int space = maxStack - existing.getQuantity();
            if (space <= 0) return;

            placed = Math.min(movedQty, space);
            overflowBack = movedQty - placed;

            targetGrid.updateSlot(
                    new ItemGridSlot(new ItemStack(itemId, existing.getQuantity() + placed)),
                    targetIndex
            );

        } else {
            // Different item in target:
            // If this was a partial move (half/one), DON'T swap (it feels wrong and can dupe).
            if (sourceStack != null && movedQty < sourceQty) return;

            // Full stack move => allow swap
            targetGrid.updateSlot(new ItemGridSlot(new ItemStack(itemId, movedQty)), targetIndex);

            if (sourceGrid != null && sourceIndex != null) {
                sourceGrid.updateSlot(
                        new ItemGridSlot(new ItemStack(existing.getItem().getId(), existing.getQuantity())),
                        sourceIndex
                );
            }
            return;
        }

        // Update source slot remaining for partial moves (THIS is the important part)
        if (sourceGrid != null && sourceIndex != null && sourceStack != null) {
            int remaining = sourceQty - movedQty;
            int finalSourceQty = remaining + overflowBack;

            if (finalSourceQty > 0) {
                sourceGrid.updateSlot(new ItemGridSlot(new ItemStack(itemId, finalSourceQty)), sourceIndex);
            } else {
                sourceGrid.updateSlot(new ItemGridSlot(), sourceIndex);
            }
        }
    }

    private void setSourceSlot(Integer sourceIndex, Integer sourceSection, ItemGridSlot slot,
                               ItemGridBuilder targetGrid, int targetSectionId,
                               int salvageSectionId, int storageSectionId,int hotbarSectionId, UIContext ctx) {
        if (sourceSection == null) return;

        if (sourceSection == targetSectionId) {
            targetGrid.updateSlot(slot, sourceIndex);
        } else if (sourceSection == salvageSectionId) {
            ctx.getById("salvage-grid", ItemGridBuilder.class).ifPresent(grid -> {
                grid.updateSlot(slot, sourceIndex);
            });
        } else if (sourceSection == storageSectionId) {
            ctx.getById("storage-grid", ItemGridBuilder.class).ifPresent(grid -> {
                grid.updateSlot(slot, sourceIndex);
            });
        }else if (sourceSection == hotbarSectionId) {
            ctx.getById("hotbar-grid", ItemGridBuilder.class).ifPresent(grid -> {
                grid.updateSlot(slot, sourceIndex); // or new ItemGridSlot() in clear
            });
        }
    }

    private void clearSourceSlot(Integer sourceIndex, Integer sourceSection,
                                 ItemGridBuilder targetGrid, int targetSectionId,
                                 int salvageSectionId, int storageSectionId,int hotbarSectionId,
                                 Integer targetIndex, UIContext ctx) {
        if (sourceSection == null) {
            return;
        }

        if (sourceSection == targetSectionId) {
            if (!sourceIndex.equals(targetIndex)) {
                targetGrid.updateSlot(new ItemGridSlot(), sourceIndex);
            }
        } else if (sourceSection == salvageSectionId) {
            ctx.getById("salvage-grid", ItemGridBuilder.class).ifPresent(grid -> {
                grid.updateSlot(new ItemGridSlot(), sourceIndex);
            });
        } else if (sourceSection == storageSectionId) {
            ctx.getById("storage-grid", ItemGridBuilder.class).ifPresent(grid -> {
                grid.updateSlot(new ItemGridSlot(), sourceIndex);
            });
        }else if (sourceSection == hotbarSectionId) {
            ctx.getById("hotbar-grid", ItemGridBuilder.class).ifPresent(grid -> {
                grid.updateSlot(new ItemGridSlot(), sourceIndex); // or new ItemGridSlot() in clear
            });
        }
    }

//    private void handleDragCancelled(int targetSectionId, int salvageSectionId, int storageSectionId,
//                                     UIContext ctx) {
//        if (dragItemId == null || dragSourceIndex < 0) {
//            return;
//        }
//
//        // Find the source grid and restore original quantity
//        ItemGridBuilder sourceGrid = null;
//        if (dragSourceSection == salvageSectionId) {
//            sourceGrid = ctx.getById("salvage-grid", ItemGridBuilder.class).orElse(null);
//        } else if (dragSourceSection == storageSectionId) {
//            sourceGrid = ctx.getById("storage-grid", ItemGridBuilder.class).orElse(null);
//        }
//
//        if (sourceGrid != null) {
//            sourceGrid.updateSlot(new ItemGridSlot(new ItemStack(
//                    dragItemId,
//                    dragOriginalQty
//            )), dragSourceIndex);
//        }
//
//    }

    private static final class DragState {
        boolean active;
        String itemId;
        int qty;
        int sourceIndex;
        int sourceSection;
        int startButton;
    }

    private static final Map<String, DragState> dragStates = new HashMap<>();

    private static DragState dragState(String uuid) {
        return dragStates.computeIfAbsent(uuid, k -> new DragState());
    }

    private static void beginDragIfNeeded(String uuid, ItemStack stack, int sourceIndex, int sourceSection) {
        DragState st = dragState(uuid);
        if (st.active) return;

        st.active = true;
        st.itemId = stack.getItem().getId();
        st.qty = stack.getQuantity();
        st.sourceIndex = sourceIndex;
        st.sourceSection = sourceSection;
    }

    private static void clearDrag(String uuid) {
        DragState st = dragStates.get(uuid);
        if (st != null) st.active = false;
    }

    private static void restoreDrag(String uuid, int salvageSectionId, int storageSectionId, int hotbarSectionId, UIContext ctx) {
        DragState st = dragStates.get(uuid);
        if (st == null || !st.active) return;

        ItemGridBuilder sourceGrid =
                (st.sourceSection == salvageSectionId) ? ctx.getById("salvage-grid", ItemGridBuilder.class).orElse(null) :
                        (st.sourceSection == storageSectionId) ? ctx.getById("storage-grid", ItemGridBuilder.class).orElse(null) :
                                (st.sourceSection == hotbarSectionId)  ? ctx.getById("hotbar-grid", ItemGridBuilder.class).orElse(null) :
                                        null;

        if (sourceGrid != null && st.sourceIndex >= 0) {
            sourceGrid.updateSlot(new ItemGridSlot(new ItemStack(st.itemId, st.qty)), st.sourceIndex);
        }

        clearDrag(uuid);
    }
}