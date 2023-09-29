package io.neocities.robotchicken.general;


import java.io.*;
import java.net.*;
import java.util.*;
import net.fabricmc.loader.api.*;
import net.minecraft.client.*;
import net.minecraft.client.network.*;
import net.minecraft.client.world.*;
import net.minecraft.network.*;
import net.minecraft.network.packet.c2s.handshake.*;
import net.minecraft.network.packet.c2s.login.*;
import net.minecraft.text.*;

public class MC {

    public static MinecraftClient mc;
    public static File FOLDER = FabricLoader.getInstance().getGameDir().resolve("stashmaker").toFile();
    public static boolean intentionalDisconnect = false;

    public static synchronized MinecraftClient client() {
        if (mc == null) mc = MinecraftClient.getInstance();
        if (mc == null) throw new IllegalStateException("client instance is null");
        return mc;
    }

    public static ClientPlayerEntity player() {
        return client().player;
    }

    public static ClientWorld world() {
        return client().world;
    }


    public static void connect() {
        ServerAddress address = new ServerAddress("0b0t.org", 25565);
        ServerInfo info = new ServerInfo("0b0t", "0b0t.org", false);
        Optional<InetSocketAddress> optional = AllowedAddressResolver.DEFAULT.resolve(address).map(Address::getInetSocketAddress);
        InetSocketAddress inetSocketAddress = optional.get();
        ClientConnection connection = ClientConnection.connect(inetSocketAddress, MC.client().options.shouldUseNativeTransport());
        connection.setPacketListener(new ClientLoginNetworkHandler(connection, MC.client(), info, null, false, null, (text) -> {
        }));
        connection.send(new HandshakeC2SPacket(inetSocketAddress.getHostName(), inetSocketAddress.getPort(), NetworkState.LOGIN));
        connection.send(new LoginHelloC2SPacket(MC.client().getSession().getUsername(), Optional.ofNullable(MC.client().getSession().getUuidOrNull())));
    }

    public static void disconnect() {
        intentionalDisconnect = true;
        mc.getNetworkHandler().getConnection().disconnect(Text.literal("intentional"));
        MC.client().disconnect();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean inGame() {
        return player() != null && world() != null;
    }
}