package com.greatdani.craftingplugin.pages;

import au.ellie.hyui.builders.*;
import au.ellie.hyui.events.*;
import au.ellie.hyui.types.ButtonStyle;
import au.ellie.hyui.types.ButtonStyleState;
import au.ellie.hyui.types.DefaultStyles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.greatdani.craftingplugin.CraftingPlugin;
import com.greatdani.craftingplugin.ResourceTypePreviewCache;
import com.greatdani.craftingplugin.crafting.CraftingRecipe;
import com.greatdani.craftingplugin.crafting.CraftingUI;
import com.greatdani.craftingplugin.crafting.RecipeRegistry;
import com.greatdani.craftingplugin.styles.MyModStyles;
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
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static com.greatdani.craftingplugin.crafting.RecipeRegistry.prettyItemId;

public class RecipeBuilderUI {

    // --- State Management ---
    private static final Map<String, CraftingRecipe> currentRecipes = new HashMap<>();
    private static final Map<String, String> selectedRecipeId = new HashMap<>();
    private static final Map<String, RecipeBuilderUI.DragState> dragStates = new HashMap<>();

    // --- Constants ---
    private static final int SALVAGE_SECTION_ID = 10;
    private static final int OUTPUT_SECTION_ID = 20;
    private static final int STORAGE_SECTION_ID = 30;

    public static void open(PlayerRef playerRef, Store<EntityStore> store, Player playerComponent, PlayerAuthentication playerAuthentication) {
        String uuid = playerAuthentication.getUuid().toString();

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .loadHtml("Pages/RecipeBuilder.html");

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

        // Setup Labels
        setupLabel(page, "my-label", "Recipe builder", 20);
        setupLabel(page, "my-recipe-label", "Build Recipe", 20);
        setupLabel(page, "inventory-label", "Inventory and Hotbar", 18);
        setupLabel(page, "crafting-label", "Recipe builder Area", 18);
        setupLabel(page, "my-info-label", "Info", 20);
    }

    private static void setupLabel(PageBuilder page, String id, String text, int fontSize) {
        page.getById(id, LabelBuilder.class).ifPresent(label -> {
            HyUIStyle style = new HyUIStyle().setFontSize(fontSize).setTextColor("#FFFF00").setAlignment(Alignment.Center).setRenderBold(true);
            HyUIPadding padding = new HyUIPadding().setBottom(10).setTop(10);
            label.withText(text).withPadding(padding).withStyle(style);
        });
    }

    private static void setupGrids(PageBuilder page, Player playerComponent) {

        // Salvage / Crafting Grid
        page.getById("salvage-grid", ItemGridBuilder.class).ifPresent(grid -> {
            grid.withInventorySectionId(SALVAGE_SECTION_ID);
            MyModStyles.applyInventorySlotStyles(grid, grid.getSlots().size());
        });

        // Output Grid
        page.getById("output-grid", ItemGridBuilder.class).ifPresent(grid -> {
            grid.withInventorySectionId(OUTPUT_SECTION_ID).withAreItemsDraggable(false);
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


        page.addEventListener("output-grid", CustomUIEventBindingType.SlotClicking, SlotClickingEventData.class, (click, ctx) -> {

            ctx.updatePage(false);
        });

        // SAVE RECIPE TO JSON BUTTON
        page.addEventListener("save-recipe-btn", CustomUIEventBindingType.Activating, (data, ctx) -> {
            // Get the name from the input box
            String recipeName = ctx.getValueAs("recipe-name-input", String.class).orElse("my_custom_recipe");
            if (!recipeName.endsWith(".json")) recipeName += ".json";

            var salvageOpt = ctx.getById("salvage-grid", ItemGridBuilder.class);
            var outputOpt = ctx.getById("output-grid", ItemGridBuilder.class);

            if (salvageOpt.isEmpty() || outputOpt.isEmpty()) return;

            ItemGridBuilder crafting = salvageOpt.get();
            ItemGridBuilder output = outputOpt.get();

            // --- CONFLICT PREVENTION ---
            CraftingRecipe conflictingRecipe = RecipeRegistry.findMatch(crafting);
            if (conflictingRecipe != null) {
                playerRef.sendMessage(Message.raw("§cCannot save: This exact pattern is already used by the recipe '" + conflictingRecipe.getId() + "'!"));
                return; // Stop the save process!
            }

            ItemGridSlot outSlot = output.getSlot(0);
            ItemStack outStack = outSlot != null ? ItemGridBuilder.getItemStack(outSlot) : null;

            if (outStack == null) {
                playerRef.sendMessage(Message.raw("§cCannot save: Output slot is empty!"));
                return;
            }

            // Generate the JSON!
            JsonObject recipeJson = generateRecipeJson(crafting, outStack);

            // Save it to your recipes folder
            try {
                // Change "recipes" to the exact folder path where RecipeRegistry loads from!
                Path folder = Paths.get(CraftingPlugin.get().getDataDirectory() + "/Recipes");
                if (!Files.exists(folder)) Files.createDirectories(folder);

                Path file = folder.resolve(recipeName);

                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                Files.writeString(file, gson.toJson(recipeJson));

                playerRef.sendMessage(Message.raw("§aSuccessfully saved recipe to " + file.toString() + "!"));
            } catch (Exception ex) {
                playerRef.sendMessage(Message.raw("§cFailed to save recipe: " + ex.getMessage()));
                ex.printStackTrace();
            }
        });
        // 1. CLEAR OUTPUT BUTTON
        page.addEventListener("clear-recipe-btn", CustomUIEventBindingType.Activating, (data, ctx) -> {
            var outputOpt = ctx.getById("output-grid", ItemGridBuilder.class);
            var storageOpt = ctx.getById("storage-grid", ItemGridBuilder.class);

            String currentFileName = ctx.getValueAs("recipe-name-input", String.class).orElse("my_custom_recipe");

            if (outputOpt.isPresent() && storageOpt.isPresent()) {
                ItemGridBuilder output = outputOpt.get();
                ItemGridBuilder storage = storageOpt.get();

                ItemGridSlot outSlot = output.getSlot(0);
                ItemStack outStack = outSlot != null ? ItemGridBuilder.getItemStack(outSlot) : null;

                if (outStack != null) {
                    // Return the item safely to the player's inventory
                    pushToGrid(outStack, storage, 45);

                    // Clear the output slot
                    output.updateSlot(new ItemGridSlot(), 0);

                    MyModStyles.applyCombinedInventoryStyles(storage);
                    MyModStyles.applyOutputSlotStyles(output);

                    ctx.getById("recipe-name-input", TextFieldBuilder.class).ifPresent(input -> {
                        input.withValue(currentFileName);
                    });

                    ctx.updatePage(false);
                }
            }
        });

        // CLEAR SALVAGE (INPUT) GRID BUTTON
        page.addEventListener("clear-salvage-recipe-btn", CustomUIEventBindingType.Activating, (data, ctx) -> {
            var salvageOpt = ctx.getById("salvage-grid", ItemGridBuilder.class);
            var storageOpt = ctx.getById("storage-grid", ItemGridBuilder.class);

            String currentFileName = ctx.getValueAs("recipe-name-input", String.class).orElse("my_custom_recipe");

            if (salvageOpt.isPresent() && storageOpt.isPresent()) {
                ItemGridBuilder salvage = salvageOpt.get();
                ItemGridBuilder storage = storageOpt.get();
                boolean changed = false;

                // Loop through all 9 slots in the crafting grid
                for (int i = 0; i < 9; i++) {
                    ItemGridSlot slot = salvage.getSlot(i);
                    ItemStack stack = slot != null ? ItemGridBuilder.getItemStack(slot) : null;

                    if (stack != null) {
                        // Safely return the item to the player's inventory
                        pushToGrid(stack, storage, 45);

                        // Empty the slot in the crafting grid
                        salvage.updateSlot(new ItemGridSlot(), i);
                        changed = true;
                    }
                }

                // If we actually cleared items, update the styles and refresh the UI!
                if (changed) {
                    MyModStyles.applyCombinedInventoryStyles(storage);
                    MyModStyles.applyInventorySlotStyles(salvage, 9);

                    ctx.getById("recipe-name-input", TextFieldBuilder.class).ifPresent(input -> {
                        input.withValue(currentFileName);
                    });

                    ctx.updatePage(false);
                }
            }
        });

        // 3. SET OUTPUT QUANTITY (Updates the item in the 1x1 output grid)
        page.addEventListener("set-output", CustomUIEventBindingType.ValueChanged, (data, ctx) -> {
            int qty = ctx.getValueAs("set-output", Double.class).orElse(1.0).intValue();
            if (qty <= 0) return;
            String currentFileName = ctx.getValueAs("recipe-name-input", String.class).orElse("my_custom_recipe");

            ctx.getById("output-grid", ItemGridBuilder.class).ifPresent(grid -> {
                ItemGridSlot slot = grid.getSlot(0);
                ItemStack stack = slot != null ? ItemGridBuilder.getItemStack(slot) : null;
                if (stack != null) {
                    grid.updateSlot(new ItemGridSlot(new ItemStack(stack.getItem().getId(), qty)), 0);
                    MyModStyles.applyOutputSlotStyles(grid);

                    ctx.getById("recipe-name-input", TextFieldBuilder.class).ifPresent(input -> {
                        input.withValue(currentFileName);
                    });

                    ctx.updatePage(false);
                }
            });
        });

        page.addEventListener("salvage-grid", CustomUIEventBindingType.Dropped, DroppedEventData.class,
                (drop, ctx) -> handleGridDrop(uuid, drop, ctx, playerComponent, "salvage-grid", SALVAGE_SECTION_ID));
        page.addEventListener("storage-grid", CustomUIEventBindingType.Dropped, DroppedEventData.class,
                (drop, ctx) -> handleGridDrop(uuid, drop, ctx, playerComponent, "storage-grid", STORAGE_SECTION_ID));
        page.addEventListener("output-grid", CustomUIEventBindingType.Dropped, DroppedEventData.class,
                (drop, ctx) -> handleGridDrop(uuid, drop, ctx, playerComponent, "output-grid", OUTPUT_SECTION_ID));

        // DRAG CANCELS
        page.addEventListener("salvage-grid", CustomUIEventBindingType.DragCancelled, DragCancelledEventData.class, (data, ctx) -> {
            restoreDrag(uuid, ctx); ctx.updatePage(true);
        });
        page.addEventListener("storage-grid", CustomUIEventBindingType.DragCancelled, DragCancelledEventData.class, (data, ctx) -> {
            restoreDrag(uuid, ctx); ctx.updatePage(true);
        });
        page.addEventListener("output-grid", CustomUIEventBindingType.DragCancelled, DragCancelledEventData.class, (data, ctx) -> {
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

        String currentFileName = ctx.getValueAs("recipe-name-input", String.class).orElse("my_custom_recipe");

        ctx.getById(targetGridId, ItemGridBuilder.class).ifPresent(grid -> {
            processDrop(drop, grid, targetSectionId, ctx);
        });

        reapplyAllInventoryStyles(ctx, player);

        ctx.getById("recipe-name-input", TextFieldBuilder.class).ifPresent(input -> {
            input.withValue(currentFileName);
        });

        ctx.updatePage(false);

    }


    private static void processDrop(DroppedEventData drop, ItemGridBuilder targetGrid, int targetSectionId, UIContext ctx) {
        Integer sourceIndex = drop.getSourceSlotId();
        Integer targetIndex = drop.getSlotIndex();
        Integer sourceSection = drop.getSourceInventorySectionId();
        Integer mouseBtn = drop.getPressedMouseButton();

        if (targetIndex == null) return;

        if (sourceIndex != null && sourceSection != null && sourceSection == targetSectionId && sourceIndex.equals(targetIndex)) {
            if (targetSectionId == SALVAGE_SECTION_ID && mouseBtn != null && mouseBtn == 3) {
                int customQty = ctx.getValueAs("set-input", Double.class).orElse(0.0).intValue();
                String dragItemId = drop.getItemStackId();

                if (customQty > 0 && dragItemId != null && !dragItemId.isEmpty()) {
                    // Prevent exceeding the max stack size
                    Item itemAsset = (Item) Item.getAssetMap().getAsset(dragItemId);
                    int maxStack = itemAsset != null ? itemAsset.getMaxStack() : 99;
                    int finalQty = Math.min(customQty, maxStack);

                    targetGrid.updateSlot(new ItemGridSlot(new ItemStack(dragItemId, finalQty)), targetIndex);
                }
            }
            return;
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

    // ==========================================
    // Utility Methods
    // ==========================================

    private static ItemStack pushToGrid(ItemStack stack, ItemGridBuilder grid, int capacity) {
        String itemId = stack.getItem().getId();
        int qty = stack.getQuantity();
        int max = stack.getItem().getMaxStack();

        // 1. Try merging into existing stacks first
        for (int i = 0; i < capacity; i++) {
            ItemGridSlot slot = grid.getSlot(i);
            ItemStack existing = slot != null ? ItemGridBuilder.getItemStack(slot) : null;
            if (existing != null && existing.getItem().getId().equals(itemId)) {
                int space = max - existing.getQuantity();
                if (space > 0) {
                    int move = Math.min(qty, space);
                    grid.updateSlot(new ItemGridSlot(new ItemStack(itemId, existing.getQuantity() + move)), i);
                    qty -= move;
                    if (qty <= 0) return null; // All placed successfully!
                }
            }
        }

        // 2. Place any remainder into completely empty slots
        for (int i = 0; i < capacity; i++) {
            ItemGridSlot slot = grid.getSlot(i);
            ItemStack existing = slot != null ? ItemGridBuilder.getItemStack(slot) : null;
            if (existing == null) {
                grid.updateSlot(new ItemGridSlot(new ItemStack(itemId, qty)), i);
                return null; // All placed successfully!
            }
        }

        return new ItemStack(itemId, qty); // Inventory is full, return what's left
    }

    private static JsonObject generateRecipeJson(ItemGridBuilder craftingGrid, ItemStack outputStack) {
        JsonObject recipeJson = new JsonObject();

        Map<String, Character> itemToChar = new HashMap<>();
        Map<Character, Integer> charToQty = new HashMap<>();
        char currentChar = 'A';
        String[] pattern = new String[3];

        // 1. Build Pattern & Extract Keys
        for (int row = 0; row < 3; row++) {
            StringBuilder rowPattern = new StringBuilder();

            for (int col = 0; col < 3; col++) {
                int idx = row * 3 + col;
                ItemGridSlot slot = craftingGrid.getSlot(idx);
                ItemStack stack = slot != null ? ItemGridBuilder.getItemStack(slot) : null;

                if (stack == null) {
                    rowPattern.append(" ");
                } else {
                    String id = stack.getItem().getId();
                    int qty = stack.getQuantity();

                    // Group by ID + Qty so 2 wood gets a different letter than 1 wood
                    String uniqueKey = id + ":" + qty;

                    if (!itemToChar.containsKey(uniqueKey)) {
                        itemToChar.put(uniqueKey, currentChar);
                        charToQty.put(currentChar, qty);
                        currentChar++;
                    }
                    rowPattern.append(itemToChar.get(uniqueKey));
                }
            }
            pattern[row] = rowPattern.toString();
        }

        // 2. Add Pattern Array to JSON
        JsonArray patternArray = new JsonArray();
        for (String p : pattern) patternArray.add(p);
        recipeJson.add("pattern", patternArray);

        // 3. Build Keys Object
        JsonObject keyObj = new JsonObject();
        for (Map.Entry<String, Character> entry : itemToChar.entrySet()) {
            String itemId = entry.getKey().split(":")[0];
            int qty = charToQty.get(entry.getValue());

            JsonObject itemData = new JsonObject();
            itemData.addProperty("item", itemId);
            if (qty > 1) {
                itemData.addProperty("quantity", qty);
            }

            keyObj.add(String.valueOf(entry.getValue()), itemData);
        }
        recipeJson.add("key", keyObj);

        // 4. Build Result Object
        JsonObject resultObj = new JsonObject();
        resultObj.addProperty("item", outputStack.getItem().getId());
        resultObj.addProperty("quantity", outputStack.getQuantity());
        recipeJson.add("result", resultObj);

        return recipeJson;
    }

    private static ItemGridBuilder getGridBySection(int sectionId, UIContext ctx) {
        String id = switch (sectionId) {
            case SALVAGE_SECTION_ID -> "salvage-grid";
            case STORAGE_SECTION_ID -> "storage-grid";
            default -> null;
        };
        return (id != null) ? ctx.getById(id, ItemGridBuilder.class).orElse(null) : null;
    }

    private static void reapplyAllInventoryStyles(UIContext ctx, Player player) {
        ctx.getById("salvage-grid", ItemGridBuilder.class).ifPresent(grid ->
                MyModStyles.applyInventorySlotStyles(grid, grid.getSlots().size()));
        ctx.getById("output-grid", ItemGridBuilder.class).ifPresent(MyModStyles::applyOutputSlotStyles);

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

    // ==========================================
    // Drag State Handling
    // ==========================================

    private static final class DragState {
        boolean active;
        String itemId;
        int qty, sourceIndex, sourceSection;
    }

    private static void beginDragIfNeeded(String uuid, ItemStack stack, int sourceIndex, int sourceSection) {
        RecipeBuilderUI.DragState st = dragStates.computeIfAbsent(uuid, k -> new RecipeBuilderUI.DragState());
        if (st.active) return;
        st.active = true;
        st.itemId = stack.getItem().getId();
        st.qty = stack.getQuantity();
        st.sourceIndex = sourceIndex;
        st.sourceSection = sourceSection;
    }

    private static void clearDrag(String uuid) {
        RecipeBuilderUI.DragState st = dragStates.get(uuid);
        if (st != null) st.active = false;
    }

    private static void restoreDrag(String uuid, UIContext ctx) {
        RecipeBuilderUI.DragState st = dragStates.get(uuid);
        if (st == null || !st.active) return;

        ItemGridBuilder sourceGrid = getGridBySection(st.sourceSection, ctx);
        if (sourceGrid != null && st.sourceIndex >= 0) {
            sourceGrid.updateSlot(new ItemGridSlot(new ItemStack(st.itemId, st.qty)), st.sourceIndex);
        }
        clearDrag(uuid);
    }
}
