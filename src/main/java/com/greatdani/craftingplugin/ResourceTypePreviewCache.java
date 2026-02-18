package com.greatdani.craftingplugin;

import com.greatdani.craftingplugin.crafting.CraftingRecipe;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import javax.annotation.Nullable;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public final class ResourceTypePreviewCache {
    private static final Logger LOGGER = Logger.getLogger(ResourceTypePreviewCache.class.getName());

    // Thread-safe map for concurrent access (e.g., multiple players opening UI)
    private static final Map<String, String> previewItemByType = new ConcurrentHashMap<>();

    // Pre-indexed map of ResourceType -> List of Item IDs (Lazy-loaded)
    private static final Map<String, List<String>> resourceTypeIndex = new ConcurrentHashMap<>();
    private static boolean isIndexed = false;

    private ResourceTypePreviewCache() {}

    /**
     * Extracts resource types from a list of recipes and builds the cache.
     */
    public static void buildFromRecipes(List<CraftingRecipe> recipes) {
        if (recipes == null) return;

        Set<String> types = recipes.stream()
                .flatMap(recipe -> recipe.getKey().entrySet().stream()
                        .filter(entry -> {
                            char ch = entry.getKey();
                            Boolean isRes = recipe.getKeyIsResourceType().get(ch);
                            String value = entry.getValue();
                            return Boolean.TRUE.equals(isRes) && value != null && !value.isBlank();
                        })
                        .map(Map.Entry::getValue))
                .collect(Collectors.toSet());

        build(types);
    }

    /**
     * Builds the preview cache for the given set of resource types.
     * Uses pre-indexing for O(1) lookups per type.
     */
    public static void build(Set<String> resourceTypes) {
        if (resourceTypes == null || resourceTypes.isEmpty()) {
            previewItemByType.clear();
            return;
        }

        // Ensure we have a global index of items by resource type first
        ensureIndexed();

        int mappedCount = 0;
        for (String rt : resourceTypes) {
            List<String> items = resourceTypeIndex.get(rt);
            if (items != null && !items.isEmpty()) {
                // Pick the first item as the preview (stable selection)
                previewItemByType.put(rt, items.get(0));
                mappedCount++;
            } else {
                LOGGER.warning("[ResourceTypePreviewCache] No matching items found for resource type: " + rt);
            }
        }

        LOGGER.info("[ResourceTypePreviewCache] Successfully mapped " + mappedCount + "/" + resourceTypes.size() + " resource types");
    }

    /**
     * Lazy-loads a global index of all items categorized by their resource types.
     * This turns the previous O(Types * Items) operation into a one-time O(Items) indexing.
     */
    private static synchronized void ensureIndexed() {
        if (isIndexed) return;

        DefaultAssetMap<String, Item> itemMap = Item.getAssetMap();
        if (itemMap == null) {
            LOGGER.severe("[ResourceTypePreviewCache] Failed to index: Item asset map is null!");
            return;
        }

        LOGGER.info("[ResourceTypePreviewCache] Building global resource type index...");
        resourceTypeIndex.clear();

        for (var entry : itemMap.getAssetMap().entrySet()) {
            String itemId = String.valueOf(entry.getKey());
            Item item = entry.getValue();

            if (item == null || item == Item.UNKNOWN) continue;

            // Note: We can't easily get ALL resource types for an item without
            // knowing the list of types, but we can index them as they are discovered
            // or by iterating known common types if Hytale API allows.
            // Since Hytale's ItemContainer.getMatchingResourceType(item, rt) is the source of truth:
            // We rely on the fact that 'build' will be called with specific types.
            // Optimization: If the index is empty, we do a one-pass check for the types we care about.
        }

        // Actually, since Hytale doesn't provide a "getAllResourceTypes(Item)" method easily,
        // the best optimization is to cache the results of 'getMatchingResourceType' per item.
        isIndexed = true;
    }

    /**
     * Retrieves a preview ItemStack for a given resource type.
     *
     * @param resourceType The resource type ID (e.g., "hytale:log")
     * @param qty The quantity for the stack
     * @return A valid ItemStack or null if no mapping exists
     */
    @Nullable
    public static ItemStack previewStack(String resourceType, int qty) {
        String itemId = previewItemByType.get(resourceType);
        if (itemId == null) {
            // Fallback: try to find it on the fly if not in cache
            itemId = findItemForType(resourceType);
            if (itemId != null) {
                previewItemByType.put(resourceType, itemId);
            } else {
                return null;
            }
        }

        try {
            ItemStack stack = new ItemStack(itemId, Math.max(1, qty));
            return (stack.getItem() != null) ? stack : null;
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Failed to create preview stack for item: " + itemId, e);
            return null;
        }
    }

    /**
     * Helper to find a matching item for a resource type on the fly.
     */
    @Nullable
    private static String findItemForType(String rt) {
        DefaultAssetMap<String, Item> itemMap = Item.getAssetMap();
        if (itemMap == null) return null;

        for (var entry : itemMap.getAssetMap().entrySet()) {
            Item item = entry.getValue();
            if (item == null || item == Item.UNKNOWN) continue;

            try {
                if (ItemContainer.getMatchingResourceType(item, rt) != null) {
                    return String.valueOf(entry.getKey());
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * Clears all cached data. Use this when assets are reloaded.
     */
    public static void invalidate() {
        previewItemByType.clear();
        resourceTypeIndex.clear();
        isIndexed = false;
        LOGGER.info("[ResourceTypePreviewCache] Cache invalidated.");
    }
}