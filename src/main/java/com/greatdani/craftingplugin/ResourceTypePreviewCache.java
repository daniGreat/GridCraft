package com.greatdani.craftingplugin;

import com.greatdani.craftingplugin.crafting.CraftingRecipe;
import com.hypixel.hytale.assetstore.map.DefaultAssetMap;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;

import java.lang.reflect.Method;
import java.util.*;

public final class ResourceTypePreviewCache {
    private static final Map<String, String> previewItemByType = new HashMap<>();

    private ResourceTypePreviewCache() {}

    public static void buildFromRecipes(List<CraftingRecipe> recipes) {
        Set<String> types = new HashSet<>();

        for (CraftingRecipe r : recipes) {
            for (Map.Entry<Character, String> e : r.getKey().entrySet()) {
                char ch = e.getKey();
                String required = e.getValue();
                Boolean isRes = r.getKeyIsResourceType().get(ch);
                if (Boolean.TRUE.equals(isRes) && required != null && !required.isBlank()) {
                    types.add(required);
                }
            }
        }

        build(types);
    }

    public static void build(Set<String> resourceTypes) {
        previewItemByType.clear();
        if (resourceTypes == null || resourceTypes.isEmpty()) return;

        DefaultAssetMap<String, Item> itemMap = Item.getAssetMap();
        if (itemMap == null) return;

        var all = itemMap.getAssetMap(); // underlying map (entrySet iteration)

        for (String rt : resourceTypes) {
            String pick = null;

            for (var entry : all.entrySet()) {
                String itemId = String.valueOf(entry.getKey());
                Item item = entry.getValue();
                if (item == null || item == Item.UNKNOWN) continue;

                try {
                    // "does this item belong to resource type rt?"
                    if (ItemContainer.getMatchingResourceType(item, rt) != null) {
                        pick = itemId;
                        break; // first match is fine (fast + stable)
                    }
                } catch (Exception ignored) {}
            }

            if (pick != null) previewItemByType.put(rt, pick);
        }

        System.out.println("[ResourceTypePreviewCache] mapped " + previewItemByType.size() + " resource types");
    }

    public static ItemStack previewStack(String resourceType, int qty) {
        String itemId = previewItemByType.get(resourceType);
        if (itemId == null) return null;

        try {
            ItemStack st = new ItemStack(itemId, Math.max(1, qty));
            return (st.getItem() != null) ? st : null;
        } catch (Exception e) {
            return null;
        }
    }
}