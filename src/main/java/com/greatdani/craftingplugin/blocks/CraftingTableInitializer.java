package com.greatdani.craftingplugin.blocks;


import com.greatdani.craftingplugin.CraftingPlugin;
import com.hypixel.hytale.component.*;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nonnull;

public class CraftingTableInitializer extends RefSystem<ChunkStore> {

    @Override
    public void onEntityAdded(@Nonnull Ref ref, @Nonnull AddReason reason, @Nonnull Store store, @Nonnull CommandBuffer commandBuffer) {
        BlockModule.BlockStateInfo info = (BlockModule.BlockStateInfo) commandBuffer.getComponent(ref, BlockModule.BlockStateInfo.getComponentType());
        if (info == null) return;

        CraftingTableBlock generator = (CraftingTableBlock) commandBuffer.getComponent(ref, CraftingPlugin.get().getCraftingTableBlockComponentType());
        if (generator != null) {
            int x = ChunkUtil.xFromBlockInColumn(info.getIndex());
            int y = ChunkUtil.yFromBlockInColumn(info.getIndex());
            int z = ChunkUtil.zFromBlockInColumn(info.getIndex());

            WorldChunk worldChunk = (WorldChunk) commandBuffer.getComponent(info.getChunkRef(), WorldChunk.getComponentType());
            if (worldChunk != null) {
                worldChunk.setTicking(x, y, z, true);
            }
        }
    }

    @Override
    public void onEntityRemove(@Nonnull Ref ref, @Nonnull RemoveReason reason, @Nonnull Store store, @Nonnull CommandBuffer commandBuffer) {
    }

    @Override
    public Query getQuery() {
        return Query.and(BlockModule.BlockStateInfo.getComponentType(), CraftingPlugin.get().getCraftingTableBlockComponentType());
    }
}
