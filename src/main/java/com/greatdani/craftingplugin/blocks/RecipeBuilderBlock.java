package com.greatdani.craftingplugin.blocks;

import com.greatdani.craftingplugin.CraftingPlugin;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.ComponentType;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nullable;

public class RecipeBuilderBlock implements Component<ChunkStore> {

    public static final BuilderCodec CODEC = BuilderCodec.builder(RecipeBuilderBlock.class, RecipeBuilderBlock::new).build();

    // Components usually require a copy constructor as well but since we don't actually hold any data in this component this is not neccessary
    public RecipeBuilderBlock() { }

    public static ComponentType getComponentType() {
        return CraftingPlugin.get().getRecipeBuilderBlockComponentType();
    }

    @Nullable
    public Component<ChunkStore> clone() {
        return new RecipeBuilderBlock();
    }
}

