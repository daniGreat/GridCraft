package com.greatdani.craftingplugin.commands;

import com.greatdani.craftingplugin.crafting.RecipeRegistry;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.permissions.HytalePermissions;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;

public class RecipeCommand extends AbstractCommandCollection {

    public RecipeCommand() {
        super("gridcraft", "Recipe commands");
        addSubCommand(new ReloadSubCommand());
    }

    public static class ReloadSubCommand extends AbstractPlayerCommand {

        public ReloadSubCommand() {
            super("reload", "Reloads all recipes from disk");
            requirePermission(HytalePermissions.fromCommand("admin"));
        }

        @Override
        protected void execute(@Nonnull CommandContext commandContext, @Nonnull Store<EntityStore> store,
                               @Nonnull Ref<EntityStore> ref, @Nonnull PlayerRef playerRef, @Nonnull World world) {
            int before = RecipeRegistry.getAllRecipes().size();
            RecipeRegistry.reload();
            int after = RecipeRegistry.getAllRecipes().size();

            commandContext.sendMessage(Message.raw("§aRecipes reloaded! " + before + " -> " + after + " recipes loaded."));
        }
    }
}