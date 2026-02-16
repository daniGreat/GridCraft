package com.greatdani.craftingplugin;

import com.greatdani.craftingplugin.blocks.CraftingTableBlock;
import com.greatdani.craftingplugin.blocks.CraftingTableInitializer;
import com.greatdani.craftingplugin.crafting.RecipeRegistry;
import com.greatdani.craftingplugin.systems.CraftingTableSystem;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.io.adapter.PacketAdapters;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.plugin.registry.AssetRegistry;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;

public class CraftingPlugin extends JavaPlugin {
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    protected static CraftingPlugin instance;
    private ComponentType craftingBlockComponentType;

    public CraftingPlugin(JavaPluginInit init) {
        super(init);
        LOGGER.atInfo().log("Hello from %s version %s", this.getName(), this.getManifest().getVersion().toString());
        instance = this;

    }

    public static CraftingPlugin get() {
        return instance;
    }

    @Override
    protected void setup() {
        this.craftingBlockComponentType = this.getChunkStoreRegistry().registerComponent(CraftingTableBlock.class, "CraftingTableBlock", CraftingTableBlock.CODEC);

        PacketAdapters.registerInbound(new PacketListener());

    }



    @Override
    protected void start() {
        Path recipesFolder = Path.of(getDataDirectory() + "/Recipes");
        RecipeRegistry.init(recipesFolder);
        ResourceTypePreviewCache.buildFromRecipes(RecipeRegistry.getAllRecipes());

        this.getChunkStoreRegistry().registerSystem(new CraftingTableInitializer());

        this.getChunkStoreRegistry().registerSystem(new CraftingTableSystem());
    }


    public ComponentType getCraftingTableBlockComponentType() {
        return this.craftingBlockComponentType;
    }

}
