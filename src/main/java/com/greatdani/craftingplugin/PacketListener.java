package com.greatdani.craftingplugin;

import com.greatdani.craftingplugin.blocks.CraftingTableBlock;
import com.greatdani.craftingplugin.blocks.RecipeBuilderBlock;
import com.greatdani.craftingplugin.crafting.CraftingUI;
import com.greatdani.craftingplugin.pages.RecipeBuilderUI;
import com.greatdani.craftingplugin.pages.TestUI;
import com.hypixel.hytale.component.Component;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChain;
import com.hypixel.hytale.protocol.packets.interaction.SyncInteractionChains;
import com.hypixel.hytale.server.core.HytaleServer;import com.hypixel.hytale.server.core.auth.PlayerAuthentication;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.io.adapter.PacketWatcher;
import com.hypixel.hytale.server.core.io.handlers.game.GamePacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockComponentChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.*;

public class PacketListener implements PacketWatcher {


    private static final boolean HAS_HYUI = checkHyUIPresent();

    // THREAD SAFETY: Wrapped in synchronizedSet to prevent multi-threading crashes
    private final Set<SyncInteractionChain> processedInteractions =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    @Override
    public void accept(PacketHandler packetHandler, Packet packet) {

        if (!(packet instanceof SyncInteractionChains interactionChains)) {
            return;
        }

        if (!HAS_HYUI) {
            CraftingPlugin.get().getLogger().atInfo().log("GridCraft requires HyUI. Install HyUI or GridCraft will not load.");
            return;
        }

        if (!(packetHandler instanceof GamePacketHandler gpHandler)) return;

        PlayerAuthentication playerAuthentication = gpHandler.getAuth();
        Ref<EntityStore> playerRef = gpHandler.getPlayerRef().getReference();
        Store<EntityStore> store = playerRef.getStore();
        World world = store.getExternalData().getWorld();

        for (SyncInteractionChain item : interactionChains.updates) {

            if (item.interactionType != InteractionType.Use || !processedInteractions.add(item)) {
                continue;
            }

            if (item.data.blockPosition != null) {
                int x = item.data.blockPosition.x;
                int y = item.data.blockPosition.y;
                int z = item.data.blockPosition.z;

                world.execute(() -> {
                    Ref<ChunkStore> blockRef = getBlockRefAt(world, x, y, z);

                    if (blockRef != null && blockRef.isValid()) {
                        var blockStore = blockRef.getStore();

                        // 1. Check if it's a Crafting Table
                        var craftingComp = blockStore.getComponent(blockRef, CraftingTableBlock.getComponentType());
                        if (craftingComp != null) {
                            gpHandler.getInteractionPacketQueue().remove(item);
                            Player playerComponent = store.getComponent(playerRef, Player.getComponentType());
                            CraftingUI.open(gpHandler.getPlayerRef(), store, playerComponent, playerAuthentication);
                            return; // Stop checking, we found it
                        }

                        // 2. Check if it's a Recipe Builder
                        var recipeComp = blockStore.getComponent(blockRef, RecipeBuilderBlock.getComponentType());
                        if (recipeComp != null) {
                            gpHandler.getInteractionPacketQueue().remove(item);
                            Player playerComponent = store.getComponent(playerRef, Player.getComponentType());
                            RecipeBuilderUI.open(gpHandler.getPlayerRef(), store, playerComponent, playerAuthentication);
                        }
                    }
                });
            }
        }
    }

    private static boolean checkHyUIPresent() {
        try {
            Class.forName("au.ellie.hyui.builders.PageBuilder", false, PacketListener.class.getClassLoader());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }


    private Ref<ChunkStore> getBlockRefAt(World world, int x, int y, int z) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(x, z);
        Store<ChunkStore> chunkStore = world.getChunkStore().getStore();
        Ref<ChunkStore> chunkRef = chunkStore.getExternalData().getChunkReference(chunkIndex);

        if (chunkRef == null || !chunkRef.isValid()) {
            return null;
        }

        BlockComponentChunk blockComponentChunk = chunkStore.getComponent(chunkRef, BlockComponentChunk.getComponentType());
        if (blockComponentChunk == null) {
            return null;
        }

        int localX = x & 31;
        int localZ = z & 31;
        return blockComponentChunk.getEntityReference(ChunkUtil.indexBlockInColumn(localX, y, localZ));
    }
    private static String getPlayerName(PacketHandler handler) {
        if (handler instanceof GamePacketHandler gpHandler) {
            return gpHandler.getPlayerRef().getUsername();
        }
        return null;
    }
}
