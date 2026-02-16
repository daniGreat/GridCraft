package com.greatdani.craftingplugin.crafting;

import au.ellie.hyui.builders.ItemGridBuilder;
import com.hypixel.hytale.protocol.ItemResourceType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.ui.ItemGridSlot;

import java.util.HashMap;
import java.util.Map;

public class CraftingRecipe {

    private final String id;
    private final String label;

    private final String[] pattern;
    private final Map<Character, String> key;
    private final Map<Character, Integer> keyQuantities;
    private final Map<Character, Boolean> keyIsResourceType;
    private final ItemStack result;

    public CraftingRecipe(String id, String name, String[] pattern, Map<Character, String> key,
                          Map<Character, Integer> keyQuantities,
                          Map<Character, Boolean> keyIsResourceType,
                          ItemStack result) {
        this.id = id;
        this.label = name;
        this.pattern = pattern;
        this.key = key;
        this.keyQuantities = keyQuantities;
        this.keyIsResourceType = keyIsResourceType;
        this.result = result;
    }

    public String getId() { return id; }
    public String getLabel() { return label; }

    public boolean matches(ItemGridBuilder grid) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                int slotIndex = row * 3 + col;
                char expected = pattern[row].charAt(col);

                ItemGridSlot slot = grid.getSlot(slotIndex);
                ItemStack stack = slot != null ? ItemGridBuilder.getItemStack(slot) : null;

                if (expected == ' ') {
                    if (stack != null) return false;
                } else {
                    if (stack == null) return false;
                    String required = key.get(expected);
                    int requiredQty = keyQuantities.getOrDefault(expected, 1);
                    Boolean isResourceType = keyIsResourceType.get(expected);

                    if (stack.getQuantity() < requiredQty) return false;

                    if (isResourceType == null) {
                        // Simple string format: try exact item ID first, then resource type
                        if (!stack.getItem().getId().equals(required)) {
                            try {
                                ItemResourceType resourceType = ItemContainer.getMatchingResourceType(stack.getItem(), required);
                                if (resourceType == null) return false;
                            } catch (Exception e) {
                                return false;
                            }
                        }
                    } else if (isResourceType) {
                        // "resourcetype" field: ONLY check resource type
                        try {
                            ItemResourceType resourceType = ItemContainer.getMatchingResourceType(stack.getItem(), required);
                            if (resourceType == null) return false;
                        } catch (Exception e) {
                            return false;
                        }
                    } else {
                        // "item" field: ONLY check exact item ID
                        if (!stack.getItem().getId().equals(required)) return false;
                    }
                }
            }
        }
        return true;
    }

    public int getRequiredQuantity(char key) {
        return keyQuantities.getOrDefault(key, 1);
    }

    public char getPatternChar(int slotIndex) {
        int row = slotIndex / 3;
        int col = slotIndex % 3;
        return pattern[row].charAt(col);
    }

    public ItemStack getResult() {
        return result;
    }

    public String[] getPattern() { return pattern; }
    public Map<Character, String> getKey() { return key; }
    public Map<Character, Integer> getKeyQuantities() { return keyQuantities; }
    public Map<Character, Boolean> getKeyIsResourceType() { return keyIsResourceType; }
}