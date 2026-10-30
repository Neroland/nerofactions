package za.co.neroland.nerofactions.platform;

import java.util.function.Predicate;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.player.Player;

/**
 * The handful of vanilla calls whose shape differs between the Minecraft lines NeroFactions builds for,
 * in one place, so gameplay code stays one copy. Each method is a straight pass-through on 26.x.
 */
public final class VersionCompat {

    private VersionCompat() {
    }

    /** A chat-line system message to one player. */
    public static void systemMessage(Player player, Component message) {
        //? if >=26.1 {
        player.sendSystemMessage(message);
        //?} else {
        /*player.displayClientMessage(message, false);
        *///?}
    }

    /** The command requirement for permission level 2 (operators). */
    public static Predicate<CommandSourceStack> gamemasters() {
        //? if >=1.21.6 {
        return Commands.hasPermission(Commands.LEVEL_GAMEMASTERS);
        //?} else {
        /*return source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS);
        *///?}
    }

    /** A click that runs {@code command} as the clicking player. */
    public static ClickEvent runCommand(String command) {
        //? if >=1.21.5 {
        return new ClickEvent.RunCommand(command);
        //?} else {
        /*return new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
        *///?}
    }

    /** A click that copies {@code text} to the clipboard. */
    public static ClickEvent copyToClipboard(String text) {
        //? if >=1.21.5 {
        return new ClickEvent.CopyToClipboard(text);
        //?} else {
        /*return new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, text);
        *///?}
    }

    /** A hover that shows {@code text}. */
    public static HoverEvent showText(Component text) {
        //? if >=1.21.5 {
        return new HoverEvent.ShowText(text);
        //?} else {
        /*return new HoverEvent(HoverEvent.Action.SHOW_TEXT, text);
        *///?}
    }
}
