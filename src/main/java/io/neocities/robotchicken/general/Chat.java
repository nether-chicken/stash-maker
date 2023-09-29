package io.neocities.robotchicken.general;

import java.util.*;
import java.util.function.*;
import java.util.regex.*;
import net.fabricmc.fabric.api.client.message.v1.*;

public class Chat {
    public static final Pattern USER_CHAT = Pattern.compile("<([a-zA-Z0-9_]{3,16})> (.+)");
    public static final Pattern CHAT_CONSOLE = Pattern.compile("\\[Server\\] (.+)");
    public static final Pattern WHISPER = Pattern.compile("([a-zA-Z0-9_]{3,16}) whispers: (.+)");
    public static final Pattern TP_REQUEST = Pattern.compile("(.*?) wants to teleport to you\\.$");
    public static final Pattern ANY = Pattern.compile(".*");
    private final Map<Pattern, Consumer<Matcher>> handlers;

    public Chat() {
        this.handlers = new HashMap<>();
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay) {
                return;
            }
            handle(message.getString());
        });
    }

    public void clearHandlers() {
        handlers.clear();
    }

    public void register(Pattern pattern, Consumer<Matcher> handler) {
        handlers.put(pattern, handler);
    }

    public void handle(String message) {
        for (Map.Entry<Pattern, Consumer<Matcher>> entry : handlers.entrySet()) {
            var matcher = entry.getKey().matcher(message);
            if (matcher.matches()) {
                entry.getValue().accept(matcher);
            }
        }
    }

    public static void send(String msg) {
        MC.player().networkHandler.sendChatMessage(msg);
    }
}
