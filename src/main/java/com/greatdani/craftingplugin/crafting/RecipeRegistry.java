package com.greatdani.craftingplugin.crafting;

import au.ellie.hyui.builders.ItemGridBuilder;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.inventory.ItemStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecipeRegistry {
    private static final List<CraftingRecipe> recipes = new ArrayList<>();
    private static final Gson gson = new Gson();
    private static final Map<String, CraftingRecipe> byId = new HashMap<>();

    private static Path recipesFolder;

    public static void init(Path recipesFolder) {
        RecipeRegistry.recipesFolder = recipesFolder;
        recipes.clear();
        byId.clear();

        try {
            if (!Files.exists(recipesFolder)) {
                Files.createDirectories(recipesFolder);
                System.out.println("Created recipes folder: " + recipesFolder);
                return;
            }


            Files.list(recipesFolder)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(path -> {
                        try {
                            String json = Files.readString(path);
                            loadRecipeFile(json, path);
                            System.out.println("Loaded recipes from: " + path.getFileName());
                        } catch (Exception e) {
                            System.err.println("Failed to load: " + path.getFileName());
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println("Loaded " + recipes.size() + " recipes.");
    }

    public static void reload() {
        if (recipesFolder != null) {
            init(recipesFolder);
        }
    }

    private static void loadRecipeFile(String json, Path path) {
        String base = path.getFileName().toString().replaceFirst("\\.json$", "");
        JsonElement element = gson.fromJson(json, JsonElement.class);

        if (element.isJsonArray()) {
            int idx = 0;
            for (JsonElement entry : element.getAsJsonArray()) {
                CraftingRecipe recipe = parseRecipe(entry.getAsJsonObject(), base + "#" + idx);
                if (recipe != null) {
                    recipes.add(recipe);
                    byId.put(recipe.getId(), recipe);
                }
                idx++;
            }
        } else if (element.isJsonObject()) {
            CraftingRecipe recipe = parseRecipe(element.getAsJsonObject(), base);
            if (recipe != null) {
                recipes.add(recipe);
                byId.put(recipe.getId(), recipe);
            }
        }
    }

    private static CraftingRecipe parseRecipe(JsonObject obj, String id) {
        String[] pattern = gson.fromJson(obj.getAsJsonArray("pattern"), String[].class);

        Map<Character, String> key = new HashMap<>();
        Map<Character, Integer> keyQuantities = new HashMap<>();
        Map<Character, Boolean> keyIsResourceType = new HashMap<>();
        JsonObject keyObj = obj.getAsJsonObject("key");

        for (Map.Entry<String, JsonElement> entry : keyObj.entrySet()) {
            char c = entry.getKey().charAt(0);

            if (entry.getValue().isJsonObject()) {
                JsonObject itemObj = entry.getValue().getAsJsonObject();
                keyQuantities.put(c, itemObj.has("quantity") ? itemObj.get("quantity").getAsInt() : 1);

                if (itemObj.has("resourcetype")) {
                    key.put(c, itemObj.get("resourcetype").getAsString());
                    keyIsResourceType.put(c, true);
                } else {
                    key.put(c, itemObj.get("item").getAsString());
                    keyIsResourceType.put(c, false);
                }
            } else {
                // Simple string format - could be ITEM ID or RESOURCE TYPE
                String val = entry.getValue().getAsString();
                key.put(c, val);
                keyQuantities.put(c, 1);

                // If it isn't a valid item id, treat it as a resource type
                boolean isValidItemId = false;
                try {
                    ItemStack test = new ItemStack(val, 1);
                    isValidItemId = (test.getItem() != null && test.getItem() != Item.UNKNOWN);
                } catch (Exception ignored) {
                    isValidItemId = false;
                }

                if (!isValidItemId) {
                    keyIsResourceType.put(c, true);
                }
            }
        }

        JsonObject resultObj = obj.getAsJsonObject("result");
        String resultItem = resultObj.get("item").getAsString();

        int resultQty = resultObj.get("quantity").getAsInt();

        // Validate result
        try {
            ItemStack rs = new ItemStack(resultItem, resultQty);
            if (rs.getItem() == null || rs.getItem() == Item.UNKNOWN) {
                System.err.println("Invalid result item: " + resultItem);
                return null;
            }
        } catch (Exception e) {
            System.err.println("Invalid result item: " + resultItem + " - must be an exact item ID, not a resource type");
            return null;
        }
        String label = prettyItemId(resultItem) + " x" + resultQty;

        return new CraftingRecipe(id, label, pattern, key, keyQuantities, keyIsResourceType, new ItemStack(resultItem, resultQty));
    }

    public static String prettyItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) return "";

        // If some ids *do* include namespace occasionally, strip it safely
        int colon = itemId.indexOf(':');
        String s = (colon >= 0) ? itemId.substring(colon + 1) : itemId;

        // Normalize separators to spaces
        s = s.replace('_', ' ').replace('-', ' ').trim();
        if (s.isEmpty()) return itemId;

        // Insert spaces for camelCase / PascalCase boundaries: "WoodTrunk" -> "Wood Trunk"
        s = s.replaceAll("([a-z])([A-Z])", "$1 $2");
        // Also split letters<->digits: "Iron2H" -> "Iron 2 H"
        s = s.replaceAll("([A-Za-z])([0-9])", "$1 $2");
        s = s.replaceAll("([0-9])([A-Za-z])", "$1 $2");

        // Collapse extra spaces
        s = s.replaceAll("\\s+", " ").trim();

        // Title-case each word, but handle mixed-case words like "wood" + "Trunk"
        StringBuilder out = new StringBuilder();
        for (String w : s.split(" ")) {
            if (w.isEmpty()) continue;

            // If it's ALL CAPS already (acronym), keep it
            if (w.equals(w.toUpperCase()) && w.length() <= 5) {
                out.append(w).append(' ');
                continue;
            }

            String lower = w.toLowerCase();
            out.append(Character.toUpperCase(lower.charAt(0)))
                    .append(lower.substring(1))
                    .append(' ');
        }

        return out.toString().trim();
    }


    public static CraftingRecipe findMatch(ItemGridBuilder grid) {
        for (CraftingRecipe recipe : recipes) {
            if (recipe.matches(grid)) return recipe;
        }
        return null;
    }


    public static List<CraftingRecipe> getAllRecipes() {
        return List.copyOf(recipes);
    }

    public static CraftingRecipe getById(String id) {
        return byId.get(id);
    }
}