package com.greatdani.craftingplugin.crafting;

import au.ellie.hyui.builders.*;
import au.ellie.hyui.events.*;
import au.ellie.hyui.types.*;
import com.greatdani.craftingplugin.ResourceTypePreviewCache;
import com.greatdani.craftingplugin.styles.MyModStyles;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ItemResourceType;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.greatdani.craftingplugin.crafting.RecipeRegistry.prettyItemId;

public class CraftingUI {

    // --- State Management ---
    private static final Map<String, CraftingRecipe> currentRecipes = new HashMap<>();
    private static final Map<String, String> selectedRecipeId = new HashMap<>();
    private static final Map<String, DragState> dragStates = new HashMap<>();

    // --- Constants ---
    private static final int SALVAGE_SECTION_ID = 10;
    private static final int OUTPUT_SECTION_ID = 20;
    private static final int STORAGE_SECTION_ID = 30;
    private static final int HOTBAR_SECTION_ID = 50;
    private static final int PREVIEW_SECTION_ID = 60;
    private static final int PREVIEW_OUTPUT_SECTION_ID = 70;

    private static final int MOUSE_BTN_RIGHT = 3;

    public static void open(PlayerRef playerRef, Store<EntityStore> store, Player playerComponent, PlayerAuthentication playerAuthentication) {
        String uuid = playerAuthentication.getUuid().toString();

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .loadHtml("Pages/GridCraft.html");

        setupStaticUI(page, uuid);
        setupGrids(page, playerComponent);
        registerEvents(page, uuid, playerRef, store, playerComponent);

        page.open(store);
    }

    // ==========================================
    // UI Initialization Methods
    // ==========================================

    private static void setupStaticUI(PageBuilder page, String uuid) {
        // Build Button Styles
        ButtonStyle customButtonStyle = new ButtonStyle()
                .withDefault(new ButtonStyleState()
                        .withBackground(new HyUIPatchStyle().setTexturePath("ContainerCloseButton.png"))
                        .withLabelStyle(new HyUIStyle().setHorizontalAlignment(Alignment.Center).setVerticalAlignment(Alignment.Center)))
                .withHovered(new ButtonStyleState()
                        .withBackground(new HyUIPatchStyle().setTexturePath("ContainerCloseButtonHovered.png"))
                        .withLabelStyle(new HyUIStyle().setHorizontalAlignment(Alignment.Center).setVerticalAlignment(Alignment.Center)))
                .withPressed(new ButtonStyleState()
                        .withBackground(new HyUIPatchStyle().setTexturePath("ContainerCloseButtonPressed.png"))
                        .withLabelStyle(new HyUIStyle().setHorizontalAlignment(Alignment.Center).setVerticalAlignment(Alignment.Center)))
                .withDisabled(new ButtonStyleState()
                        .withBackground(new HyUIPatchStyle())
                        .withLabelStyle(new HyUIStyle().setHorizontalAlignment(Alignment.Center).setVerticalAlignment(Alignment.Center)))
                .withSounds(DefaultStyles.buttonSounds());

        page.getById("my-button-uttn", ButtonBuilder.class).ifPresent(button -> {
            button.withText("");
            button.withStyle(customButtonStyle);
        });

        page.getById("craft-button", ButtonBuilder.class).ifPresent(button -> {
            button.withText("Auto-Fill");
        });

        page.getById("moveto-inventory-button", ButtonBuilder.class).ifPresent(button -> {
            button.withText("Clear Grid");
        });

        page.getById("Recipe", DropdownBoxBuilder.class).ifPresent(dropdown -> {
            for (CraftingRecipe r : RecipeRegistry.getAllRecipes()) {
                if (r.getId() == null || r.getId().isBlank()) continue;
                String label = (r.getLabel() == null || r.getLabel().isBlank()) ? r.getId() : r.getLabel();
                dropdown.addEntry(r.getId(), label);
            }
            dropdown.addEventListener(CustomUIEventBindingType.ValueChanged, (selectedId, ctx) -> {
                if (selectedId == null || selectedId.isBlank()) return;
                selectedRecipeId.put(uuid, selectedId);

                CraftingRecipe r = RecipeRegistry.getById(selectedId);
                if (r == null) return;

                updateRecipePreviewGrid(ctx, r);
                updateRecipeOutputPreview(ctx, r);
                ctx.updatePage(false);
            });
            dropdown.withStyle(new HyUIStyle());
        });

        // Setup Labels
        setupLabel(page, "my-label", "Crafting Table", 20);
        setupLabel(page, "my-recipe-label", "Recipes", 20);
        setupLabel(page, "inventory-label", "Inventory and Hotbar", 18);
        setupLabel(page, "crafting-label", "Crafting Area", 18);
    }

    private static void setupLabel(PageBuilder page, String id, String text, int fontSize) {
        page.getById(id, LabelBuilder.class).ifPresent(label -> {
            HyUIStyle style = new HyUIStyle().setFontSize(fontSize).setTextColor("#FFFF00").setAlignment(Alignment.Center).setRenderBold(true);
            HyUIPadding padding = new HyUIPadding().setBottom(10).setTop(10);
            label.withText(text).withPadding(padding).withStyle(style);
        });
    }

    private static void setupGrids(PageBuilder page, Player playerComponent) {
        // Preview Input
        page.getById("terminal-view", ItemGridBuilder.class).ifPresent(grid -> {
            grid.withInventorySectionId(PREVIEW_SECTION_ID).withAreItemsDraggable(false);
            int have = (grid.getSlots() == null) ? 0 : grid.getSlots().size();
            for (int i = have; i < 9; i++) grid.addSlot(new ItemGridSlot());
        });

        // Preview Output
        page.getById("output-terminal-view", ItemGridBuilder.class).ifPresent(grid -> {
            grid.withInventorySectionId(PREVIEW_OUTPUT_SECTION_ID).withAreItemsDraggable(false);
            if (grid.getSlots() == null || grid.getSlots().isEmpty()) grid.addSlot(new ItemGridSlot());
        });

        // Salvage / Crafting Grid
        page.getById("salvage-grid", ItemGridBuilder.class).ifPresent(grid -> {
            grid.withInventorySectionId(SALVAGE_SECTION_ID);
            MyModStyles.applyInventorySlotStyles(grid, grid.getSlots().size());
        });

        // Output Grid
        page.getById("output-grid", ItemGridBuilder.class).ifPresent(grid -> {
            grid.withInventorySectionId(OUTPUT_SECTION_ID).withAllowMaxStackDraggableItems(false).withAreItemsDraggable(true);
            MyModStyles.applyOutputSlotStyles(grid);
        });

        page.getById("storage-grid", ItemGridBuilder.class).ifPresent(grid -> {
            grid.withInventorySectionId(STORAGE_SECTION_ID); // We only need 1 ID now!

            ItemContainer storage = playerComponent.getInventory().getStorage();
            ItemContainer hotbar = playerComponent.getInventory().getHotbar();

            // Slots 0-35 (Storage)
            for (int slot = 0; slot < storage.getCapacity(); ++slot) {
                ItemStack s = storage.getItemStack((short) slot);
                grid.updateSlot((s != null) ? new ItemGridSlot(s) : new ItemGridSlot(), slot);
            }

            // Slots 36-44 (Hotbar)
            for (int slot = 0; slot < hotbar.getCapacity(); ++slot) {
                ItemStack s = hotbar.getItemStack((short) slot);
                grid.updateSlot((s != null) ? new ItemGridSlot(s) : new ItemGridSlot(), slot + storage.getCapacity());
            }

            MyModStyles.applyCombinedInventoryStyles(grid);
        });
    }

    // ==========================================
    // Event Registration
    // ==========================================

    private static void registerEvents(PageBuilder page, String uuid, PlayerRef playerRef, Store<EntityStore> store, Player playerComponent) {
        page.addEventListener("my-button-uttn", CustomUIEventBindingType.Activating, (data, ctx) -> {
            ctx.getPage().ifPresent(p -> p.close());
        });

        page.onDismiss((ctx, wasForceClosed) -> handleDismiss(uuid, playerRef, store, playerComponent, ctx));

        page.addEventListener("moveto-inventory-button", CustomUIEventBindingType.Activating, (data, ctx) -> {
            clearSalvageToInventory(ctx);
            checkRecipes(uuid, ctx);
            ctx.updatePage(false);
        });

        // AUTO-FILL BUTTON
        page.addEventListener("craft-button", CustomUIEventBindingType.Activating, (data, ctx) -> {
            int requestedMultiplier = ctx.getValueAs("auto-fill-qty", Double.class).orElse(1.0).intValue();
            if (requestedMultiplier < 1) requestedMultiplier = 1;

            String recId = selectedRecipeId.get(uuid);
            if (recId == null) return;
            CraftingRecipe recipe = RecipeRegistry.getById(recId);
            if (recipe == null) return;

            var salvageOpt = ctx.getById("salvage-grid", ItemGridBuilder.class);
            var storageOpt = ctx.getById("storage-grid", ItemGridBuilder.class);
            if (salvageOpt.isEmpty() || storageOpt.isEmpty()) return;

            ItemGridBuilder salvage = salvageOpt.get();
            ItemGridBuilder storage = storageOpt.get();

            clearSalvageToInventory(ctx);

            String[] pattern = recipe.getPattern();
            if (pattern == null || pattern.length < 3) return;

            // 1. Calculate how many items are needed for ONE full craft of this recipe
            Map<Character, Integer> charCountPerCraft = new HashMap<>();
            for (int row = 0; row < 3; row++) {
                String line = pattern[row] == null ? "" : pattern[row];
                if (line.length() < 3) line = String.format("%-3s", line);
                if (line.length() > 3) line = line.substring(0, 3);
                for (int col = 0; col < 3; col++) {
                    char ch = line.charAt(col);
                    if (ch != ' ') {
                        int baseQty = recipe.getKeyQuantities().getOrDefault(ch, 1);
                        charCountPerCraft.put(ch, charCountPerCraft.getOrDefault(ch, 0) + baseQty);
                    }
                }
            }

            int finalMultiplier = requestedMultiplier;

            // 2. SMART CALCULATOR: Check inventory to see how many we can ACTUALLY afford
            for (Map.Entry<Character, Integer> entry : charCountPerCraft.entrySet()) {
                char ch = entry.getKey();
                int totalNeededPerCraft = entry.getValue();

                String requiredId = recipe.getKey().get(ch);
                Boolean isRes = recipe.getKeyIsResourceType().get(ch);
                boolean treatAsResource = Boolean.TRUE.equals(isRes);

                // Tally up total available items in inventory
                int totalAvailable = 0;
                String bestId = null;
                Map<String, Integer> availableMatches = new HashMap<>();

                for (int i = 0; i < 45; i++) {
                    ItemGridSlot slot = storage.getSlot(i);
                    ItemStack existing = slot != null ? ItemGridBuilder.getItemStack(slot) : null;
                    if (existing != null) {
                        String existingId = existing.getItem().getId();
                        boolean match = false;
                        if (!treatAsResource) {
                            match = existingId.equals(requiredId);
                        } else {
                            Item itemAsset = (Item) Item.getAssetMap().getAsset(existingId);
                            ItemResourceType[] rts = itemAsset.getResourceTypes();
                            if (rts != null) {
                                for (ItemResourceType rt : rts) {
                                    if (rt != null && rt.id != null && rt.id.equals(requiredId)) {
                                        match = true;
                                        break;
                                    }
                                }
                            }
                        }
                        if (match) {
                            int qty = availableMatches.getOrDefault(existingId, 0) + existing.getQuantity();
                            availableMatches.put(existingId, qty);
                            if (qty > totalAvailable) {
                                totalAvailable = qty;
                                bestId = existingId;
                            }
                        }
                    }
                }

                if (bestId == null) {
                    finalMultiplier = 0;
                    break;
                }

                // Restrict multiplier based on how much the player actually has
                int possibleCrafts = totalAvailable / totalNeededPerCraft;
                if (possibleCrafts < finalMultiplier) {
                    finalMultiplier = possibleCrafts;
                }

                // Restrict multiplier so we don't exceed the Item's Max Stack Size in a single slot!
                Item itemAsset = (Item) Item.getAssetMap().getAsset(bestId);
                int maxStack = itemAsset != null ? itemAsset.getMaxStack() : 99;
                int maxBaseQtyInSingleSlot = recipe.getKeyQuantities().getOrDefault(ch, 1);
                int maxCraftsForStackSize = maxStack / maxBaseQtyInSingleSlot;

                if (maxCraftsForStackSize < finalMultiplier) {
                    finalMultiplier = maxCraftsForStackSize;
                }
            }

            // 3. Actually fill the grid evenly using the smart multiplier!
            if (finalMultiplier > 0) {
                for (int row = 0; row < 3; row++) {
                    String line = pattern[row] == null ? "" : pattern[row];
                    if (line.length() < 3) line = String.format("%-3s", line);
                    if (line.length() > 3) line = line.substring(0, 3);

                    for (int col = 0; col < 3; col++) {
                        int idx = row * 3 + col;
                        char ch = line.charAt(col);
                        if (ch == ' ') continue;

                        String requiredId = recipe.getKey().get(ch);
                        if (requiredId == null) continue;

                        Boolean isRes = recipe.getKeyIsResourceType().get(ch);
                        boolean treatAsResource = Boolean.TRUE.equals(isRes);
                        int baseQty = recipe.getKeyQuantities().getOrDefault(ch, 1);

                        int targetQty = baseQty * finalMultiplier;

                        // Pull the items
                        ItemStack pulledStack = takeFromGrid(requiredId, treatAsResource, targetQty, storage, 45);
                        if (pulledStack != null) {
                            salvage.updateSlot(new ItemGridSlot(pulledStack), idx);
                        }
                    }
                }
            }

            checkRecipes(uuid, ctx);
            MyModStyles.applyCombinedInventoryStyles(storage);
            MyModStyles.applyInventorySlotStyles(salvage, 9);
            ctx.updatePage(false);
        });

        page.addEventListener("output-grid", CustomUIEventBindingType.SlotClicking, SlotClickingEventData.class, (click, ctx) -> {
            attemptCrafting(uuid, ctx, playerComponent);
            ctx.updatePage(false);
        });

        page.addEventListener("salvage-grid", CustomUIEventBindingType.Dropped, DroppedEventData.class,
                (drop, ctx) -> handleGridDrop(uuid, drop, ctx, playerComponent, "salvage-grid", SALVAGE_SECTION_ID));
        page.addEventListener("storage-grid", CustomUIEventBindingType.Dropped, DroppedEventData.class,
                (drop, ctx) -> handleGridDrop(uuid, drop, ctx, playerComponent, "storage-grid", STORAGE_SECTION_ID));

        // DRAG CANCELS
        page.addEventListener("salvage-grid", CustomUIEventBindingType.DragCancelled, DragCancelledEventData.class, (data, ctx) -> {
            restoreDrag(uuid, ctx); ctx.updatePage(true);
        });
        page.addEventListener("storage-grid", CustomUIEventBindingType.DragCancelled, DragCancelledEventData.class, (data, ctx) -> {
            restoreDrag(uuid, ctx); ctx.updatePage(true);
        });
    }

    // ==========================================
    // Event Handlers
    // ==========================================

    private static void handleDismiss(String uuid, PlayerRef playerRef, Store<EntityStore> store, Player playerComponent, UIContext ctx) {
        currentRecipes.remove(uuid);
        dragStates.remove(uuid);

        ItemContainer storage = playerComponent.getInventory().getStorage();
        ItemContainer hotbar = playerComponent.getInventory().getHotbar();
        int storageCap = storage.getCapacity();

        // Save Unified Grid to Inventory & Hotbar
        ctx.getById("storage-grid", ItemGridBuilder.class).ifPresent(grid -> {
            // Save Storage (0-35)
            for (int slot = 0; slot < storageCap; slot++) {
                ItemGridSlot gridSlot = grid.getSlot(slot);
                storage.setItemStackForSlot((short) slot, gridSlot != null ? ItemGridBuilder.getItemStack(gridSlot) : null);
            }
            // Save Hotbar (36-44)
            for (int slot = 0; slot < hotbar.getCapacity(); slot++) {
                ItemGridSlot gridSlot = grid.getSlot(slot + storageCap);
                hotbar.setItemStackForSlot((short) slot, gridSlot != null ? ItemGridBuilder.getItemStack(gridSlot) : null);
            }
        });

        // Return salvage items to player or drop them
        ctx.getById("salvage-grid", ItemGridBuilder.class).ifPresent(salvageGrid -> {
            for (int i = 0; i < salvageGrid.getSlots().size(); i++) {
                ItemGridSlot gridSlot = salvageGrid.getSlot(i);
                ItemStack stack = gridSlot != null ? ItemGridBuilder.getItemStack(gridSlot) : null;
                if (stack == null) continue;

                stack = tryMergeOrPlaceInContainer(stack, storage);
                if (stack != null) stack = tryMergeOrPlaceInContainer(stack, hotbar);
                if (stack != null) ItemUtils.dropItem(playerRef.getReference(), stack, store);
            }
        });
    }

    private static void handleGridDrop(String uuid, DroppedEventData drop, UIContext ctx, Player player, String targetGridId, int targetSectionId) {
        if (drop.getSlotIndex() == null) {
            restoreDrag(uuid, ctx);
            ctx.updatePage(false);
            return;
        }

        if (drop.getSourceInventorySectionId() != null && drop.getSourceInventorySectionId() == OUTPUT_SECTION_ID) {
            attemptCrafting(uuid, ctx, player);
            ctx.updatePage(false);
            return;
        }

        ctx.getById(targetGridId, ItemGridBuilder.class).ifPresent(grid -> {
            processDrop(drop, grid, targetSectionId, ctx);
        });

        reapplyAllInventoryStyles(ctx, player);
        checkRecipes(uuid, ctx);
        ctx.updatePage(false);

    }

    private static void handleDragClick(String uuid, SlotClickPressWhileDraggingEventData e, int sourceSectionId, UIContext ctx) {

        if (e.getDragSourceInventorySectionId() == null || e.getDragSourceInventorySectionId() != sourceSectionId) {
            return;
        }

        Integer mouseBtn = e.getClickMouseButton();

        if (mouseBtn == null || mouseBtn == 1) return;

        // ONLY process Middle Click (2) and Right Click (3)
        if (mouseBtn != 2 && mouseBtn != 3) return;

        Integer sourceIndex = e.getDragSourceSlotId();
        Integer targetIndex = e.getSlotIndex();

        if (sourceIndex == null || targetIndex == null || targetIndex < 0) return;

        if (sourceIndex.equals(targetIndex)) {
            return;
        }

        ItemGridBuilder sourceGrid = getGridBySection(sourceSectionId, ctx);

        if (sourceGrid == null || targetIndex >= sourceGrid.getSlots().size()) return;

        ItemGridSlot srcSlot = sourceGrid.getSlot(sourceIndex);
        ItemStack src = (srcSlot != null) ? ItemGridBuilder.getItemStack(srcSlot) : null;

        if (src == null || src.getQuantity() < 1) return;

        String itemId = src.getItem().getId();
        int maxStack = src.getItem().getMaxStack();

        ItemGridSlot dstSlot = sourceGrid.getSlot(targetIndex);
        ItemStack dst = (dstSlot != null) ? ItemGridBuilder.getItemStack(dstSlot) : null;

        // Determine how much to drop
        int moveQty = 0;
        if (mouseBtn == 2) {
            // MIDDLE CLICK: Drop Half
            moveQty = Math.max(1, src.getQuantity() / 2);
        } else if (mouseBtn == 3) {
            // RIGHT CLICK: Drop exactly 1
            moveQty = 1;
        }

        int actuallyMoved = 0;
        if (dst == null) {
            actuallyMoved = moveQty;
            sourceGrid.updateSlot(new ItemGridSlot(new ItemStack(itemId, actuallyMoved)), targetIndex);
        } else if (dst.getItem().getId().equals(itemId)) {
            int space = maxStack - dst.getQuantity();
            if (space <= 0) return; // Slot full
            actuallyMoved = Math.min(moveQty, space);
            sourceGrid.updateSlot(new ItemGridSlot(new ItemStack(itemId, dst.getQuantity() + actuallyMoved)), targetIndex);
        } else {
            return;
        }

        if (actuallyMoved <= 0) return;


        int newSrcQty = src.getQuantity() - actuallyMoved;
        if (newSrcQty > 0) {
            sourceGrid.updateSlot(new ItemGridSlot(new ItemStack(itemId, newSrcQty)), sourceIndex);
        } else {
            sourceGrid.updateSlot(new ItemGridSlot(), sourceIndex);

        }
    }

    private static void processDrop(DroppedEventData drop, ItemGridBuilder targetGrid, int targetSectionId, UIContext ctx) {
        Integer sourceIndex = drop.getSourceSlotId();
        Integer targetIndex = drop.getSlotIndex();
        Integer sourceSection = drop.getSourceInventorySectionId();
        Integer mouseBtn = drop.getPressedMouseButton();

        if (targetIndex == null) return;

        if (sourceIndex != null && sourceSection != null && sourceSection == targetSectionId && sourceIndex.equals(targetIndex)) {
            return; // Dropped on self
        }

        ItemGridBuilder sourceGrid = (sourceSection != null) ? getGridBySection(sourceSection, ctx) : null;

        ItemStack sourceStack = null;
        int sourceQty = 0;
        if (sourceGrid != null && sourceIndex != null) {
            ItemGridSlot s = sourceGrid.getSlot(sourceIndex);
            sourceStack = s != null ? ItemGridBuilder.getItemStack(s) : null;
            if (sourceStack != null) sourceQty = sourceStack.getQuantity();
        }

        String itemId = drop.getItemStackId();
        int movedQty = drop.getItemStackQuantity();


        if (mouseBtn != null) {
            if (mouseBtn == 3) {
                // MIDDLE CLICK Release: Drop Half
                movedQty = sourceStack != null ? Math.max(1, sourceQty / 2) : Math.max(1, movedQty / 2);
            } else if (mouseBtn == 2) {
                // RIGHT CLICK Release: Drop exactly 1
                movedQty = 1;
            }
        }

        if (movedQty <= 0) return;

        if (sourceStack != null) {
            movedQty = Math.min(movedQty, sourceQty);
            if (movedQty <= 0) return;
        }

        ItemGridSlot targetSlot = targetGrid.getSlot(targetIndex);
        ItemStack existingTarget = targetSlot != null ? ItemGridBuilder.getItemStack(targetSlot) : null;

        int overflowBack = 0;

        if (existingTarget == null) {
            targetGrid.updateSlot(new ItemGridSlot(new ItemStack(itemId, movedQty)), targetIndex);
        } else if (existingTarget.getItem().getId().equals(itemId)) {
            int space = existingTarget.getItem().getMaxStack() - existingTarget.getQuantity();
            if (space <= 0) return;

            int placed = Math.min(movedQty, space);
            overflowBack = movedQty - placed;
            targetGrid.updateSlot(new ItemGridSlot(new ItemStack(itemId, existingTarget.getQuantity() + placed)), targetIndex);
        } else {
            // Swap items (Only allow swapping if they dropped the FULL stack!)
            if (sourceStack != null && movedQty < sourceQty) return;

            targetGrid.updateSlot(new ItemGridSlot(new ItemStack(itemId, movedQty)), targetIndex);
            if (sourceGrid != null && sourceIndex != null) {
                sourceGrid.updateSlot(new ItemGridSlot(new ItemStack(existingTarget.getItem().getId(), existingTarget.getQuantity())), sourceIndex);
            }
            return;
        }

        // Send any remaining/un-dropped items safely back to the source slot
        if (sourceGrid != null && sourceIndex != null && sourceStack != null) {
            int finalSourceQty = (sourceQty - movedQty) + overflowBack;
            if (finalSourceQty > 0) {
                sourceGrid.updateSlot(new ItemGridSlot(new ItemStack(itemId, finalSourceQty)), sourceIndex);
            } else {
                sourceGrid.updateSlot(new ItemGridSlot(), sourceIndex);
            }
        }
    }

    private static void handleSplitSafe(String uuid, SlotClickPressWhileDraggingEventData e, int targetSectionId, UIContext ctx) {
        Integer mouseBtn = e.getClickMouseButton();

        if (mouseBtn == null || (mouseBtn != 1 && mouseBtn != 3)) {
            return; // Ignore invalid clicks
        }

        Integer sourceIndex = e.getDragSourceSlotId();
        Integer targetIndex = e.getSlotIndex();
        Integer sourceSection = e.getDragSourceInventorySectionId();

        // ---- DEBUG PRINT 1 ----
        System.out.println("[SPLIT DEBUG] SourceSec: " + sourceSection + ", SourceIdx: " + sourceIndex +
                "  -->  TargetSec: " + targetSectionId + ", TargetIdx: " + targetIndex);

        if (sourceIndex == null || sourceSection == null) {
            System.out.println("[SPLIT DEBUG] Aborted: Source Index or Section is null!");
            return;
        }
        if (targetIndex == null || targetIndex < 0) {
            restoreDrag(uuid, ctx);
            return;
        }

        ItemGridBuilder targetGrid = getGridBySection(targetSectionId, ctx);
        ItemGridBuilder sourceGrid = getGridBySection(sourceSection, ctx);

        // ---- DEBUG PRINT 2 ----
        if (sourceGrid == null || targetGrid == null) {
            System.out.println("[SPLIT DEBUG] Aborted: Grids not found! SourceGrid Exists? " + (sourceGrid != null) +
                    " | TargetGrid Exists? " + (targetGrid != null));
            restoreDrag(uuid, ctx);
            return;
        }

        if (sourceGrid == targetGrid && sourceIndex.equals(targetIndex)) return;

        ItemGridSlot srcSlot = sourceGrid.getSlot(sourceIndex);
        ItemStack src = (srcSlot != null) ? ItemGridBuilder.getItemStack(srcSlot) : null;

        // ---- DEBUG PRINT 3 ----
        if (src == null || src.getQuantity() < 1) {
            System.out.println("[SPLIT DEBUG] Aborted: Source stack is empty or null!");
            clearDrag(uuid);
            return;
        }

        beginDragIfNeeded(uuid, src, sourceIndex, sourceSection);

        String itemId = src.getItem().getId();
        int maxStack = src.getItem().getMaxStack();

        ItemGridSlot dstSlot = targetGrid.getSlot(targetIndex);
        ItemStack dst = (dstSlot != null) ? ItemGridBuilder.getItemStack(dstSlot) : null;

        int moveQty = (mouseBtn == 3) ? Math.max(1, src.getQuantity() / 2) : 1;
        int actuallyMoved = 0;
        ItemStack newDst = null;

        if (dst == null) {
            actuallyMoved = moveQty;
            newDst = new ItemStack(itemId, actuallyMoved);
        } else if (dst.getItem().getId().equals(itemId)) {
            int space = maxStack - dst.getQuantity();
            if (space <= 0) {
                System.out.println("[SPLIT DEBUG] Aborted: Target slot is full!");
                clearDrag(uuid);
                return;
            }
            actuallyMoved = Math.min(moveQty, space);
            newDst = new ItemStack(itemId, dst.getQuantity() + actuallyMoved);
        } else {
            System.out.println("[SPLIT DEBUG] Aborted: Items do not match!");
            clearDrag(uuid);
            return;
        }

        if (actuallyMoved <= 0) {
            clearDrag(uuid);
            return;
        }

        targetGrid.updateSlot(new ItemGridSlot(newDst), targetIndex);

        int newSrcQty = src.getQuantity() - actuallyMoved;
        if (newSrcQty > 0) {
            sourceGrid.updateSlot(new ItemGridSlot(new ItemStack(itemId, newSrcQty)), sourceIndex);
        } else {
            sourceGrid.updateSlot(new ItemGridSlot(), sourceIndex);
        }

        // ---- DEBUG PRINT 4 ----
        System.out.println("[SPLIT DEBUG] SUCCESS! Split " + actuallyMoved + " items across grids.");
        clearDrag(uuid);
    }

    // ==========================================
    // Crafting Logic
    // ==========================================

    private static void attemptCrafting(String uuid, UIContext ctx, Player playerComponent) {
        var outputGridOpt = ctx.getById("output-grid", ItemGridBuilder.class);
        var storageGridOpt = ctx.getById("storage-grid", ItemGridBuilder.class);
        if (outputGridOpt.isEmpty() || storageGridOpt.isEmpty()) return;

        ItemGridBuilder outputGrid = outputGridOpt.get();
        ItemGridBuilder storageGrid = storageGridOpt.get();

        ItemGridSlot outSlot = outputGrid.getSlot(0);
        ItemStack outputStack = outSlot != null ? ItemGridBuilder.getItemStack(outSlot) : null;
        if (outputStack == null) return;

        int capacity = playerComponent.getInventory().getStorage().getCapacity();
        boolean placed = tryInsertIntoGrid(outputStack, storageGrid, capacity);

        CraftingRecipe recipe = currentRecipes.get(uuid);
        if (placed && recipe != null) {
            consumeIngredients(ctx, recipe);
            checkRecipes(uuid, ctx);
        }

        MyModStyles.applyCombinedInventoryStyles(storageGrid);
    }

    private static void checkRecipes(String uuid, UIContext ctx) {
        ctx.getById("salvage-grid", ItemGridBuilder.class).ifPresent(salvageGrid -> {
            CraftingRecipe recipe = RecipeRegistry.findMatch(salvageGrid);
            currentRecipes.put(uuid, recipe);

            ctx.getById("output-grid", ItemGridBuilder.class).ifPresent(outputGrid -> {
                outputGrid.updateSlot(recipe != null ? new ItemGridSlot(recipe.getResult()) : new ItemGridSlot(), 0);
                MyModStyles.applyOutputSlotStyles(outputGrid);
            });
            MyModStyles.applyInventorySlotStyles(salvageGrid, salvageGrid.getSlots().size());
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

                int newQty = stack.getQuantity() - recipe.getRequiredQuantity(patternChar);
                salvageGrid.updateSlot(newQty > 0 ? new ItemGridSlot(new ItemStack(stack.getItem().getId(), newQty)) : new ItemGridSlot(), i);
            }
        });
    }

    // ==========================================
    // Grid Setup & Previews
    // ==========================================

    private static void updateRecipePreviewGrid(UIContext ctx, CraftingRecipe recipe) {
        ctx.getById("terminal-view", ItemGridBuilder.class).ifPresent(grid -> {
            if (grid.getSlots() == null || grid.getSlots().size() < 9) return;

            String[] pattern = recipe.getPattern();
            if (pattern == null || pattern.length < 3) return;

            Map<String, Integer> totals = new HashMap<>();

            for (int row = 0; row < 3; row++) {
                // Use the row line from the pattern array
                String line = pattern[row] == null ? "" : pattern[row];
                if (line.length() < 3) line = String.format("%-3s", line);
                if (line.length() > 3) line = line.substring(0, 3);

                for (int col = 0; col < 3; col++) {
                    int idx = row * 3 + col;
                    char ch = line.charAt(col);

                    if (ch == ' ') {
                        grid.updateSlot(new ItemGridSlot(), idx);
                        continue;
                    }

                    String required = recipe.getKey().get(ch);


                    if (required == null) {
                        grid.updateSlot(new ItemGridSlot(), idx);
                        continue;
                    }

                    int qty = recipe.getKeyQuantities().getOrDefault(ch, 1);
                    Boolean isRes = recipe.getKeyIsResourceType().get(ch);

                    ItemStack preview = safePreviewStack(required, qty);
                    boolean treatAsResourceType = Boolean.TRUE.equals(isRes) || preview == null;

                    if (treatAsResourceType) {
                        ItemStack rtPreview = ResourceTypePreviewCache.previewStack(required, qty);
                        if (rtPreview != null) preview = rtPreview;
                    }

                    grid.updateSlot(preview == null ? new ItemGridSlot() : new ItemGridSlot(preview), idx);
                    totals.merge((treatAsResourceType ? "R:" : "I:") + required, qty, Integer::sum);
                }
            }

            StringBuilder out = new StringBuilder();
            for (var e : totals.entrySet()) {
                String k = e.getKey();
                int totalQty = e.getValue();
                boolean isResource = k.startsWith("R:");
                String id = k.substring(2);

                if (isResource) {
                    out.append("ResourceType: Any ").append(prettyItemId(id)).append(" x").append(totalQty).append("\n");
                } else {
                    out.append("RecipeRequires: ").append(getItemDisplayName(id)).append(" x").append(totalQty).append("\n");
                }
            }

            ctx.getById("recipe-req-text", LabelBuilder.class).ifPresent(l -> {
                l.withStyle(new HyUIStyle().setTextColor("#4287f5").setAlignment(Alignment.Center));
                l.withText(out.toString().trim());
            });
        });
    }

    private static void updateRecipeOutputPreview(UIContext ctx, CraftingRecipe recipe) {
        ctx.getById("output-terminal-view", ItemGridBuilder.class).ifPresent(grid -> {
            if (grid.getSlots() == null || grid.getSlots().isEmpty()) return;
            ItemStack res = (recipe != null) ? recipe.getResult() : null;
            grid.updateSlot(res == null ? new ItemGridSlot() : new ItemGridSlot(res), 0);
        });
    }

    // ==========================================
    // Utility Methods
    // ==========================================

    private static ItemGridBuilder getGridBySection(int sectionId, UIContext ctx) {
        String id = switch (sectionId) {
            case SALVAGE_SECTION_ID -> "salvage-grid";
            case STORAGE_SECTION_ID -> "storage-grid";
            case HOTBAR_SECTION_ID -> "hotbar-grid";
            default -> null;
        };
        return (id != null) ? ctx.getById(id, ItemGridBuilder.class).orElse(null) : null;
    }

    private static void reapplyAllInventoryStyles(UIContext ctx, Player player) {
        ctx.getById("salvage-grid", ItemGridBuilder.class).ifPresent(grid ->
                MyModStyles.applyInventorySlotStyles(grid, grid.getSlots().size()));

        ctx.getById("storage-grid", ItemGridBuilder.class).ifPresent(MyModStyles::applyCombinedInventoryStyles);
    }

    private static boolean tryInsertIntoGrid(ItemStack itemStack, ItemGridBuilder gridBuilder, int gridCapacity) {
        String itemId = itemStack.getItem().getId();
        int qty = itemStack.getQuantity();

        // Try merge
        for (int slot = 0; slot < gridCapacity; slot++) {
            ItemGridSlot existingSlot = gridBuilder.getSlot(slot);
            ItemStack existing = existingSlot != null ? ItemGridBuilder.getItemStack(existingSlot) : null;
            if (existing != null && existing.getItem().getId().equals(itemId)) {
                int total = existing.getQuantity() + qty;
                if (total <= existing.getItem().getMaxStack()) {
                    gridBuilder.updateSlot(new ItemGridSlot(new ItemStack(itemId, total)), slot);
                    return true;
                }
            }
        }
        // Try Empty
        for (int slot = 0; slot < gridCapacity; slot++) {
            ItemGridSlot existingSlot = gridBuilder.getSlot(slot);
            if (existingSlot == null || ItemGridBuilder.getItemStack(existingSlot) == null) {
                gridBuilder.updateSlot(new ItemGridSlot(new ItemStack(itemId, qty)), slot);
                return true;
            }
        }
        return false;
    }

    private static ItemStack tryMergeOrPlaceInContainer(ItemStack stack, ItemContainer container) {
        int capacity = container.getCapacity();
        String itemId = stack.getItem().getId();

        // 1. Try to merge
        for (short slot = 0; slot < capacity; slot++) {
            ItemStack existing = container.getItemStack(slot);
            if (existing != null && existing.getItem().getId().equals(itemId)) {
                int maxStack = existing.getItem().getMaxStack();
                int total = existing.getQuantity() + stack.getQuantity();

                if (total <= maxStack) {
                    container.setItemStackForSlot(slot, new ItemStack(itemId, total));
                    return null; // Placed fully
                } else {
                    container.setItemStackForSlot(slot, new ItemStack(itemId, maxStack));
                    stack = new ItemStack(itemId, total - maxStack); // Keep trying to place remainder
                }
            }
        }
        // 2. Try to find empty slot
        for (short slot = 0; slot < capacity; slot++) {
            if (container.getItemStack(slot) == null) {
                container.setItemStackForSlot(slot, stack);
                return null; // Placed fully
            }
        }
        return stack; // Return unplaced remainder
    }

    private static ItemStack safePreviewStack(String id, int qty) {
        if (id == null || id.isBlank()) return null;
        try {
            ItemStack st = new ItemStack(id, Math.max(1, qty));
            if (st.getItem() == null || st.getItem().getId() == null || st.getItem().getId().isBlank()) return null;
            return st;
        } catch (Exception ex) {
            return null;
        }
    }

    @Nonnull
    private static String getItemDisplayName(@Nonnull String itemId) {
        if (itemId.isBlank()) return itemId;

        Item item = (Item) Item.getAssetMap().getAsset(itemId);
        if (item == null || item == Item.UNKNOWN) return prettyItemId(itemId);

        String translationKey = item.getTranslationKey();
        if (translationKey == null || translationKey.isBlank()) return prettyItemId(itemId);

        String translated = I18nModule.get().getMessage("en-US", translationKey);
        return (translated != null && !translated.isBlank()) ? translated : prettyItemId(itemId);
    }

    private static void clearSalvageToInventory(UIContext ctx) {
        var salvageOpt = ctx.getById("salvage-grid", ItemGridBuilder.class);
        var storageOpt = ctx.getById("storage-grid", ItemGridBuilder.class);
        if (salvageOpt.isEmpty() || storageOpt.isEmpty()) return;

        ItemGridBuilder salvage = salvageOpt.get();
        ItemGridBuilder storage = storageOpt.get();
        boolean changed = false;

        for (int i = 0; i < 9; i++) {
            ItemGridSlot sSlot = salvage.getSlot(i);
            ItemStack sStack = sSlot != null ? ItemGridBuilder.getItemStack(sSlot) : null;
            if (sStack == null) continue;

            ItemStack remainder = pushToGrid(sStack, storage, 45);
            if (remainder == null) {
                salvage.updateSlot(new ItemGridSlot(), i); // Fully moved
                changed = true;
            } else if (remainder.getQuantity() != sStack.getQuantity()) {
                salvage.updateSlot(new ItemGridSlot(remainder), i); // Partially moved
                changed = true;
            }
        }

        if (changed) {
            MyModStyles.applyCombinedInventoryStyles(storage);
            MyModStyles.applyInventorySlotStyles(salvage, 9);
        }
    }

    private static ItemStack pushToGrid(ItemStack stack, ItemGridBuilder grid, int capacity) {
        String itemId = stack.getItem().getId();
        int qty = stack.getQuantity();
        int max = stack.getItem().getMaxStack();

        // Try merging into existing stacks
        for (int i = 0; i < capacity; i++) {
            ItemGridSlot slot = grid.getSlot(i);
            ItemStack existing = slot != null ? ItemGridBuilder.getItemStack(slot) : null;
            if (existing != null && existing.getItem().getId().equals(itemId)) {
                int space = max - existing.getQuantity();
                if (space > 0) {
                    int move = Math.min(qty, space);
                    grid.updateSlot(new ItemGridSlot(new ItemStack(itemId, existing.getQuantity() + move)), i);
                    qty -= move;
                    if (qty <= 0) return null; // All placed
                }
            }
        }
        // Place remainder in empty slots
        for (int i = 0; i < capacity; i++) {
            ItemGridSlot slot = grid.getSlot(i);
            ItemStack existing = slot != null ? ItemGridBuilder.getItemStack(slot) : null;
            if (existing == null) {
                grid.updateSlot(new ItemGridSlot(new ItemStack(itemId, qty)), i);
                return null; // All placed
            }
        }
        return new ItemStack(itemId, qty); // Inventory is full, return what's left
    }

    private static boolean hasResourceType(Item itemAsset, String desiredId) {
        if (itemAsset == null) return false;
        Object rtsObj = itemAsset.getResourceTypes();
        if (rtsObj == null) return false;

        // If it's a Collection
        if (rtsObj instanceof Collection<?> c) {
            for (Object o : c) {
                if (o == null) continue;
                if (desiredId.equals(o)) return true;
                if (desiredId.equals(o.toString())) return true;
            }
            return false;
        }

        // If it's an array
        if (rtsObj.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(rtsObj);
            for (int i = 0; i < len; i++) {
                Object o = java.lang.reflect.Array.get(rtsObj, i);
                if (o == null) continue;
                if (desiredId.equals(o)) return true;
                if (desiredId.equals(o.toString())) return true;
            }
            return false;
        }

        // Unknown type
        return desiredId.equals(rtsObj.toString());
    }

    private static ItemStack takeFromGrid(String desiredId, boolean isResource, int requiredQty, ItemGridBuilder grid, int capacity) {
        // First pass: Find all items that match the requirement
        Map<String, Integer> availableMatches = new HashMap<>();

        for (int i = 0; i < capacity; i++) {
            ItemGridSlot slot = grid.getSlot(i);
            ItemStack existing = slot != null ? ItemGridBuilder.getItemStack(slot) : null;
            if (existing != null) {
                String existingId = existing.getItem().getId();
                boolean match = false;

                if (!isResource) {
                    match = existingId.equals(desiredId);
                } else {
                    // It's a resource type! Check if the item has this resource type attached.
                    Item itemAsset = (Item) Item.getAssetMap().getAsset(existingId);
                    ItemResourceType[] rts = itemAsset.getResourceTypes();
                    if (rts != null) {
                        for (ItemResourceType rt : rts) {
                            if (rt != null && rt.id != null && rt.id.equals(desiredId)) {
                                match = true;
                                break;
                            }
                        }
                    }
                }

                if (match) {
                    availableMatches.put(existingId, availableMatches.getOrDefault(existingId, 0) + existing.getQuantity());
                }
            }
        }

        // Find the matching Item ID that we have the MOST of
        String chosenId = null;
        int maxAvailable = 0;
        for (Map.Entry<String, Integer> entry : availableMatches.entrySet()) {
            if (entry.getValue() > maxAvailable) {
                chosenId = entry.getKey();
                maxAvailable = entry.getValue();
            }
        }

        if (chosenId == null || maxAvailable <= 0) return null; // Player does not have this item


        Item itemAsset = (Item) Item.getAssetMap().getAsset(chosenId);
        int maxStack = itemAsset != null ? itemAsset.getMaxStack() : 99; // Fallback to 99 if missing

        int actualTake = Math.min(requiredQty, maxAvailable);
        actualTake = Math.min(actualTake, maxStack);

        // Second pass: Actually remove the calculated quantity from the grid!
        int remainingToTake = actualTake;
        for (int i = 0; i < capacity && remainingToTake > 0; i++) {
            ItemGridSlot slot = grid.getSlot(i);
            ItemStack existing = slot != null ? ItemGridBuilder.getItemStack(slot) : null;

            if (existing != null && existing.getItem().getId().equals(chosenId)) {
                int take = Math.min(existing.getQuantity(), remainingToTake);
                remainingToTake -= take;
                int newQty = existing.getQuantity() - take;

                if (newQty > 0) {
                    grid.updateSlot(new ItemGridSlot(new ItemStack(chosenId, newQty)), i);
                } else {
                    grid.updateSlot(new ItemGridSlot(), i);
                }
            }
        }

        return new ItemStack(chosenId, actualTake);
    }

    // ==========================================
    // Drag State Handling
    // ==========================================

    private static final class DragState {
        boolean active;
        String itemId;
        int qty, sourceIndex, sourceSection;
    }

    private static void beginDragIfNeeded(String uuid, ItemStack stack, int sourceIndex, int sourceSection) {
        DragState st = dragStates.computeIfAbsent(uuid, k -> new DragState());
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

    private static void restoreDrag(String uuid, UIContext ctx) {
        DragState st = dragStates.get(uuid);
        if (st == null || !st.active) return;

        ItemGridBuilder sourceGrid = getGridBySection(st.sourceSection, ctx);
        if (sourceGrid != null && st.sourceIndex >= 0) {
            sourceGrid.updateSlot(new ItemGridSlot(new ItemStack(st.itemId, st.qty)), st.sourceIndex);
        }
        clearDrag(uuid);
    }
}