package com.greatdani.craftingplugin.pages;

import au.ellie.hyui.builders.*;
import au.ellie.hyui.types.DefaultStyles;
import au.ellie.hyui.types.TextButtonStyle;
import au.ellie.hyui.types.TextButtonStyleState;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.server.core.auth.PlayerAuthentication;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

public class TestUI {

    public static void open(PlayerRef playerRef, Store<EntityStore> store, Player playerComponent, PlayerAuthentication playerAuthentication) {
        String uuid = playerAuthentication.getUuid().toString();

        PageBuilder page = PageBuilder.pageForPlayer(playerRef)
                .withLifetime(CustomPageLifetime.CanDismiss)
                .loadHtml("Pages/Test.html");

        HyUIPatchStyle defaultBg = new HyUIPatchStyle()
                .setColor("#D1543B");

        HyUIPatchStyle hoveredBg = new HyUIPatchStyle()
                .setColor("#BA2D11");

        HyUIPatchStyle pressedBg = new HyUIPatchStyle()
                .setColor("#D1543B");

        HyUIPatchStyle disabledBg = new HyUIPatchStyle()
                .setColor("#D1543B");

        HyUIStyle defaultLabel = new HyUIStyle()
                .setTextColor("#ffff00")
                .setFontSize(50)
                .setRenderBold(true)
                .setHorizontalAlignment(Alignment.Center)
                .setVerticalAlignment(Alignment.Center);

        HyUIStyle hoveredLabel = new HyUIStyle()
                .setTextColor("#888888")  // Yellow on hover
                .setFontSize(16)
                .setRenderBold(true)
                .setHorizontalAlignment(Alignment.Center)
                .setVerticalAlignment(Alignment.Center);

        HyUIStyle disabledLabel = new HyUIStyle()
                .setTextColor("#888888")  // Gray when disabled
                .setFontSize(16)
                .setRenderBold(true)
                .setHorizontalAlignment(Alignment.Center)
                .setVerticalAlignment(Alignment.Center);

// Create text button style
        TextButtonStyle customTextButtonStyle = new TextButtonStyle()
                .withDefault((TextButtonStyleState) new TextButtonStyleState()
                        .withBackground(defaultBg)
                        .withLabelStyle(defaultLabel))
                .withHovered((TextButtonStyleState) new TextButtonStyleState()
                        .withBackground(hoveredBg)
                        .withLabelStyle(hoveredLabel))
                .withPressed((TextButtonStyleState) new TextButtonStyleState()
                        .withBackground(pressedBg)
                        .withLabelStyle(defaultLabel))  // Same as default
                .withDisabled((TextButtonStyleState) new TextButtonStyleState()
                        .withBackground(disabledBg)
                        .withLabelStyle(disabledLabel))
                .withSounds(DefaultStyles.buttonSounds());

        page.getById("my-button-uttn", ButtonBuilder.class).ifPresent(button -> {


            button.withText("DELETE");
            button.withStyle(customTextButtonStyle);
        });

        page.open(store);
    }
}
