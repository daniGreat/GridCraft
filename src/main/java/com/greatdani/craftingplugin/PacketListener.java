package com.greatdani.craftingplugin;

import com.greatdani.craftingplugin.blocks.CraftingTableBlock;
import com.greatdani.craftingplugin.crafting.CraftingUI;
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


    // Track processed interactions to prevent double-handling
    private final Set<SyncInteractionChain> processedInteractions =
            Collections.newSetFromMap(new WeakHashMap<>());

    @Override
    public void accept(PacketHandler packetHandler, Packet packet) {
        if (packet.getId() != 290) {
            return;
        }
        SyncInteractionChains interactionChains = (SyncInteractionChains) packet;
        SyncInteractionChain[] updates = interactionChains.updates;

        for (SyncInteractionChain item : updates) {
            // Skip if already processed
            if (processedInteractions.contains(item)) {
                continue;
            }

            PlayerAuthentication playerAuthentication = packetHandler.getAuth();
            String uuid = playerAuthentication.getUuid().toString();
            InteractionType interactionType = item.interactionType;
            String playerName = getPlayerName(packetHandler);

            if(interactionType == InteractionType.Use){
                if (!isHyUIPresent()) {
                    CraftingPlugin.get().getLogger().atInfo().log("GridCraft requires HyUI. Install HyUI or GridCraft will not load.");
                    return; // don’t register listeners / don’t open UI / effectively disable
                }
                if (packetHandler instanceof GamePacketHandler gpHandler) {
                    Ref<EntityStore> playerRef = gpHandler.getPlayerRef().getReference();

                    Store<EntityStore> store = playerRef.getStore();
                    World world = store.getExternalData().getWorld();
                    // Check if this interaction has block target data
                    if (item.data.blockPosition != null) {
                        int x = item.data.blockPosition.x;
                        int y = item.data.blockPosition.y;
                        int z = item.data.blockPosition.z;

                        world.execute(() -> {
                            Ref<ChunkStore> blockRef = getBlockRefAt(world, x, y, z);

                            if (blockRef != null && blockRef.isValid()) {
                                var blockStore = blockRef.getStore();
                                Component<ChunkStore> component = blockStore.getComponent(blockRef, CraftingTableBlock.getComponentType());

                                if (component instanceof CraftingTableBlock) {
                                    // Mark as processed
                                    processedInteractions.add(item);

                                    // Remove from normal queue so game doesn't process it
                                    gpHandler.getInteractionPacketQueue().remove(item);
                                    PlayerRef  playerRef1 = gpHandler.getPlayerRef();
                                    Player playerComponent = store.getComponent(playerRef, Player.getComponentType());

                                    CraftingUI.open(playerRef1,store,playerComponent,playerAuthentication);
                                    //TestUI.open(playerRef1,store,playerComponent,playerAuthentication);

                                }
                            }
                        });
                    }
                }
            }
        }
    }

    private static boolean isHyUIPresent() {
        try {
            Class.forName("au.ellie.hyui.builders.PageBuilder", false, PacketListener.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (Throwable t) {
            // HyUI exists but failed to load for some reason
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
