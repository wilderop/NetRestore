package com.wilder0p.netrestore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

final class Messages {

    private Messages() {}

    static Component prefix() {
        return Component.text("[NetRestore] ", NamedTextColor.AQUA);
    }

    static Component offer(RestoreOffer offer) {
        return prefix()
                .append(Component.text("You died during a server network spike. ", NamedTextColor.GRAY))
                .append(Component.text(offer.snapshot.itemCount + " item stacks", NamedTextColor.WHITE))
                .append(Component.text(" saved for ", NamedTextColor.GRAY))
                .append(Component.text(offer.secondsLeft() + "s. ", NamedTextColor.WHITE))
                .append(Component.text("[Restore]", NamedTextColor.GREEN, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/restore confirm"))
                        .hoverEvent(HoverEvent.showText(Component.text(
                                "Confirm restore\n" + offer.cause + " @ " + offer.shortLoc()
                                        + "\nIncident #" + offer.incidentId, NamedTextColor.GREEN))));
    }

    static Component info(String text) {
        return prefix().append(Component.text(text, NamedTextColor.GRAY));
    }

    static Component ok(String text) {
        return prefix().append(Component.text(text, NamedTextColor.GREEN));
    }

    static Component err(String text) {
        return prefix().append(Component.text(text, NamedTextColor.RED));
    }
}
