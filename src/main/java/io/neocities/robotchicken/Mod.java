package io.neocities.robotchicken;

import baritone.api.*;
import baritone.api.pathing.goals.*;
import io.neocities.robotchicken.util.*;
import io.neocities.robotchicken.modules.Module;
import io.neocities.robotchicken.general.*;
import io.neocities.robotchicken.modules.*;
import java.util.*;
import java.util.stream.*;
import net.fabricmc.api.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.*;
import net.fabricmc.fabric.api.client.networking.v1.*;
import net.minecraft.block.*;
import net.minecraft.client.gui.screen.multiplayer.*;
import net.minecraft.client.network.*;
import net.minecraft.item.*;
import net.minecraft.nbt.visitor.*;
import net.minecraft.screen.slot.*;
import net.minecraft.util.math.*;
import static io.neocities.robotchicken.general.Chat.*;
import static io.neocities.robotchicken.general.MC.*;

public class Mod implements ModInitializer, ClientModInitializer {
    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    private Chat chat = null;
    public List<Module> modules;
    private StashMakerModule stashMakerModule;

    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.
    }

    private void initializeRest() {
        Log.info("Initializing rest");
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            var netHandler = client.getNetworkHandler();
            if (netHandler == null) return;
            var serverInfo = netHandler.getServerInfo();
            if (serverInfo == null) return;
            Log.info("client connected to %s (%s)", serverInfo.name, serverInfo.address);
            configureBaritone();
            modules = List.of(StashMakerModule.INSTANCE, AutoEatModule.INSTANCE, KillAura.INSTANCE, NoGameMenuScreenModule.INSTANCE, SwapModule.INSTANCE);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!MC.inGame()) return;
            if (MC.player().isDead()) {
                Log.info("requesting respawn");
                MC.player().requestRespawn();
                return;
            }
            if (MC.player().getHealth() < 5) {
                Log.info("Low health, disconnecting! What happened?");
                MC.disconnect();
            }
            for (Module module : modules)
                module.onTick();
//            for (AbstractClientPlayerEntity player : MC.world().getPlayers()) {
//                if (player.getUuid().equals(MC.player().getUuid())) continue;
////                Log.info("player in close proximity: %s", player.getGameProfile().getName(), player.getUuid().toString());
//            }
            if (chat == null)
                chat = new Chat();
            chat.register(TP_REQUEST, matcher -> {
                String username = matcher.group(1);
                if (username.equals("_Nether_Chicken")) {
                    send("/tpy _Nether_Chicken");
                }
                if (username.equals("_Robot_Chicken")) {
                    send("/tpy _Robot_Chicken");
                    StashMakerModule.askedChickenBot = null;
                    StashMakerModule.setTicksNextChickenBotRequest();
                }
            });

            chat.register(USER_CHAT, matcher -> {
                String username = matcher.group(1);
                String message = matcher.group(2);
                if (username.equals("_Robot_Chicken")) {
                    boolean chickenBotAnswerWait = message.matches("^(< )?Wait, .*$");
                    boolean chickenBotAnswerFarAway = message.matches("I'm too far away from the stash. This shouldn't happen. Try again in a few seconds");
                    if (StashMakerModule.askedChickenBot != null && (chickenBotAnswerWait || chickenBotAnswerFarAway)) {
                        Log.info("_Robot_Chicken asked to wait!!");
                        StashMakerModule.setTicksNextChickenBotRequest();
                        StashMakerModule.askedChickenBot = null;
                    }
                }
            });
            chat.register(ANY, matcher -> {
                String message = matcher.group(0);
                boolean serverRestarting = message.matches("^\\[Server\\] Server restarting or shutting down in ..? seconds...$");
                boolean serverCrashed = message.matches("^Exception Connecting:ReadTimeoutException : null$");

                // implement auto reconnect
            });
            chat.register(WHISPER, matcher -> {
                String username = matcher.group(1);
                String message = matcher.group(2);
                if (username.equals("_Robot_Chicken") && message.equals("wait")) {
                    Log.info("_Robot_Chicken asked to wait!!");
                    StashMakerModule.setTicksNextChickenBotRequest();
                    StashMakerModule.askedChickenBot = null;
                }
                if (!username.equals("_Nether_Chicken") && !username.equals("_Robot_Duck"))
                    return;
//            if (message.equals("shulker")) {
//                BlockPos duckKitPosition = BlockUtils.findBlockNearby(Blocks.LIGHT_BLUE_SHULKER_BOX, 5);
//                BlockUtils.interact(duckKitPosition);
//                new Thread(InvUtils.getShulkerItems()).start();
//            }
                if (message.equals("build")) {
                    BaritoneAPI.getProvider().getPrimaryBaritone().getBuilderProcess().build("ministash.schem", mc.player.getBlockPos().add(-1, 1, -1));
                }
                if (message.equals("inventory")) {
                    Log.info("Shulkers: ");
                    for (int i = 0; i < 100; i++) {
                        try {
                            ItemStack stack = mc.player.getInventory().getStack(i);
                            if (InvUtils.SHULKERS.contains(stack.getItem()))
                                Log.info(new StringNbtWriter().apply(stack.getNbt()));
                        } catch (Exception e) {
                            Log.error(e.getMessage());
                        }
                    }
                    Log.info("Done listing shulkers");
                }
                if (message.startsWith("goto")) {
                    Goal goal = parseCoordsAsGoal(message.replace("goto ", ""));
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(goal);
                }
                if (message.startsWith("here")) {
                    AbstractClientPlayerEntity acpe = mc.world.getPlayers().stream().filter(player -> player.getDisplayName().getString().equals("_Nether_Chicken")).findFirst().get();
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalNear(acpe.getBlockPos(), 1));
                }
                if (message.equals("run")) {
                    StashMakerModule.INSTANCE.start();
                }
                if (message.startsWith("checkpoint")) {
                    try {
                        int number = Integer.parseInt(message.replace("checkpoint ", ""));
                        GoalXZ lastCheckpoint = StashMakerModule.computeNthSpiralPoint(number);
                        Log.info("Going to " + lastCheckpoint);
                        BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(lastCheckpoint);
                    } catch (Exception e) {
                        Log.info("Can't parse checkpoint integer");
                    }
                }
                if (message.equals("stop")) {
                    StashMakerModule.INSTANCE.state = null;
                    StashMakerModule.stop = true;
                    BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoal(null);
                }
                if (message.equals("swap chest")) {
                    SwapModule.moving = Items.CHEST;
                }
                if (message.equals("swap trapped")) {
                    SwapModule.moving = Items.TRAPPED_CHEST;
                }
                if (message.equals("hotbar")) {
                    Log.info("!!");
                    new Thread(() -> {
                        int slot = InvUtils.findInInventory(stack -> InvUtils.nbtContains(stack, "Duck")).slot();
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, SlotUtils.indexToId(slot), 0, SlotActionType.PICKUP, mc.player);
                        try {
                            Thread.sleep(150);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, SlotUtils.indexToId(SlotUtils.HOTBAR_START + 5), 0, SlotActionType.PICKUP, mc.player);
                        try {
                            Thread.sleep(150);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, SlotUtils.indexToId(slot), 0, SlotActionType.PICKUP, mc.player);
                        try {
                            Thread.sleep(150);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }).start();
                }
            });
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            if (!MC.intentionalDisconnect) {
                Log.warn("Unintentionally disconnected! Reconnecting in 5s");
                try {
                    Thread.sleep(5 * 1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                StashMakerModule.INSTANCE.stop = true;
                chat.clearHandlers();
                if (mc.currentScreen instanceof MultiplayerScreen) {
                    return;
                }
                connect();
            } else {
                Log.info("Intentionally disconnected");
            }
            MC.intentionalDisconnect = false;
        });
    }

    @Override
    public void onInitializeClient() {
        Log.info("Initializing client");
        if (!FOLDER.exists()) {
            FOLDER.getParentFile().mkdirs();
            FOLDER.mkdir();
        }
        initializeRest();
    }

    private Goal parseCoordsAsGoal(String coords) {
        BlockPos bp = parseCoords(coords);
        if (bp.getY() == -1)
            return new GoalXZ(bp.getX(), bp.getZ());
        else
            return new GoalBlock(bp);
    }

    private BlockPos parseCoords(String coords) {
        String[] coordsArray = coords.split("[, ]");
        if (coordsArray.length == 3) {
            try {
                int x = Integer.parseInt(coordsArray[0].trim());
                int y = Integer.parseInt(coordsArray[1].trim());
                int z = Integer.parseInt(coordsArray[2].trim());
                return new BlockPos(x, y, z);
            } catch (NumberFormatException e) {
                Log.info("Invalid format for coords.");
            }
        } else if (coordsArray.length == 2) {
            try {
                int x = Integer.parseInt(coordsArray[0].trim());
                int z = Integer.parseInt(coordsArray[1].trim());
                return new BlockPos(x, 64, z);
            } catch (NumberFormatException e) {
                Log.info("Invalid format for coords.");
            }
        } else {
            Log.info("Argument does not contain enough values.");
        }
        return null;
    }

    private void configureBaritone() {
        var settings = BaritoneAPI.getSettings();
        settings.freeLook.value = false;
        settings.allowBreak.value = true;
        settings.allowPlace.value = true;
        settings.allowInventory.value = true;
        settings.allowJumpAt256.value = true;
        settings.allowWaterBucketFall.value = true;
        settings.maxFallHeightBucket.value = 256;
        settings.allowParkour.value = true;
        settings.allowParkourPlace.value = true;
        settings.allowParkourAscend.value = true;
        settings.allowDiagonalDescend.value = true;
        settings.allowDiagonalAscend.value = true;
        settings.exploreForBlocks.value = true;

        // assume no jesus as default
        settings.assumeWalkOnWater.value = false;
        settings.assumeWalkOnLava.value = false;

        settings.allowDownward.value = true;
        settings.allowVines.value = false;
        settings.allowWalkOnBottomSlab.value = true;
        settings.enterPortal.value = true;

        settings.considerPotionEffects.value = true;
        settings.rightClickContainerOnArrival.value = true;

        settings.replantCrops.value = true;
        settings.replantNetherWart.value = true;

        settings.mineScanDroppedItems.value = true;
        settings.legitMine.value = false;

        settings.pauseMiningForFallingBlocks.value = true;
        settings.avoidUpdatingFallingBlocks.value = true;

        settings.buildIgnoreExisting.value = true;
        settings.okIfWater.value = true;

        settings.avoidance.value = true;
        settings.mobSpawnerAvoidanceRadius.value = 16;
        settings.mobAvoidanceRadius.value = 16;

        settings.followOffsetDistance.value = 4d;
        settings.followRadius.value = 8;

        settings.echoCommands.value = true;
        settings.prefixControl.value = true;
        settings.chatControl.value = false;
        settings.chatControlAnyway.value = false;
        settings.chatDebug.value = false;

        // we dont see this stuff anyways
        settings.renderPath.value = false;
        settings.renderPathAsLine.value = false;
        settings.renderGoal.value = false;
        settings.renderSelectionBoxes.value = false;
        settings.renderGoalXZBeacon.value = false;
        settings.renderCachedChunks.value = false;
        settings.renderSelection.value = false;
        settings.renderSelectionCorners.value = false;
        settings.desktopNotifications.value = false;
        settings.buildIgnoreProperties.value.clear();
        settings.buildIgnoreProperties.value.add("facing");
        settings.buildIgnoreProperties.value.add("type");
        settings.buildIgnoreProperties.value.add("waterlogged");
        settings.buildIgnoreDirection.value = true;
        settings.allowSprint.value = true;

        //settings.acceptableThrowawayItems.value.addAll(BlockGroups.PATHING_BLOCKS.items);

        settings.maxFallHeightNoWater.value = 20;
        settings.blocksToAvoidBreaking.value.clear();
        settings.blocksToAvoidBreaking.value.addAll(List.of(Blocks.CHEST, Blocks.ENDER_CHEST, Blocks.RED_BED));
        settings.blocksToAvoid.value.clear();
        settings.blocksToAvoid.value.add(Blocks.VINE);
        settings.blocksToDisallowBreaking.value.clear();
        settings.blocksToDisallowBreaking.value.addAll(List.of(Blocks.CHEST, Blocks.ENDER_CHEST, Blocks.RED_BED));

        Log.info("Baritone initialized with settings:\n" + settings.allSettings.stream().map(s -> "  - " + s.getName() + ": " + s.value).collect(Collectors.joining("\n")));

        /*
         *       TODO: check hunger levels
         *     public final Settings$Setting<Boolean> sprintInWater;
         *
         *       TODO: sync from module states
         *     public final Settings$Setting<Boolean> antiCheatCompatibility;
         *     public final Settings$Setting<Boolean> assumeExternalAutoTool;
         *     public final Settings$Setting<Boolean> assumeWalkOnWater;
         *     public final Settings$Setting<Boolean> assumeWalkOnLava;
         *     public final Settings$Setting<Boolean> assumeStep;
         *     public final Settings$Setting<Boolean> assumeSafeWalk;
         *
         *       TODO: builder mode
         *     public final Settings$Setting<Boolean> buildInLayers;
         *     public final Settings$Setting<Boolean> layerOrder;
         *     public final Settings$Setting<Integer> startAtLayer;
         *     public final Settings$Setting<Boolean> skipFailedLayers;
         *     public final Settings$Setting<Boolean> mapArtMode;
         *     public final Settings$Setting<Boolean> schematicOrientationX;
         *     public final Settings$Setting<Boolean> schematicOrientationY;
         *     public final Settings$Setting<Boolean> schematicOrientationZ;
         *
         */
    }
}