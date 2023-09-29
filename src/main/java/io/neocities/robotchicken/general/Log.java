package io.neocities.robotchicken.general;

import net.minecraft.client.*;
import net.minecraft.client.gui.hud.*;
import net.minecraft.text.*;
import org.apache.logging.log4j.*;

public class Log {
    private static final String prefix = "[BOT] ";
    private static final Logger log = LogManager.getLogger("BOT");
    private static boolean logInHudToo = true;

    private static String fmt(String format, Object[] args) {
        return String.format(format, args);
    }

    public static void info(String message) {
        log.info(prefix + message);
        if (logInHudToo) {
            hud(message);
        }
    }

    public static void hud(String message) {
        InGameHud inGameHud = MinecraftClient.getInstance().inGameHud;
        if (inGameHud != null)
            inGameHud.getChatHud().addMessage(Text.literal(prefix + message));
    }

    public static void info(String format, String... args) {
        info(fmt(format, args));
    }

    public static void warn(String message) {
        log.warn(prefix + message);
        if (logInHudToo) {
            hud(message);
        }
    }

    public static void warn(String format, String... args) {
        warn(fmt(format, args));
    }

    public static void error(String message) {
        log.error(prefix + message);
        if (logInHudToo) {
            hud(message);
        }
    }

    public static void error(String format, String... args) {
        error(fmt(format, args));
    }

}
