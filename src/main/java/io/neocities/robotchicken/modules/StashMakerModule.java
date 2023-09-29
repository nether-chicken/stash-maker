package io.neocities.robotchicken.modules;

import baritone.api.*;
import baritone.api.pathing.goals.*;
import io.neocities.robotchicken.util.*;
import io.neocities.robotchicken.general.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import net.minecraft.block.*;
import net.minecraft.client.gui.screen.*;
import net.minecraft.client.network.*;
import net.minecraft.command.argument.EntityAnchorArgumentType.*;
import net.minecraft.item.*;
import net.minecraft.nbt.visitor.*;
import net.minecraft.screen.slot.*;
import net.minecraft.util.*;
import net.minecraft.util.hit.*;
import net.minecraft.util.math.*;
import static io.neocities.robotchicken.modules.StashMakerModule.State.*;
import static io.neocities.robotchicken.general.MC.*;
import static net.minecraft.item.Items.*;
import static net.minecraft.screen.slot.SlotActionType.*;

public class StashMakerModule extends Module {

    private long ticksStartMovingDuckKit;
    private int placeSlot;
    private long ticksStartNearerCenter;

    public enum State {
        SHOULD_PATH,
        PATHING_SPIRAL_POINT,
        PATH_STASH_PLATFORM,
        CHECKING_REQUIREMENTS,
        REPLENISHING_SELF_1,
        BUILDING_ROOM_1,
        BUILDING_ROOM_2,
        OPENING_TRAPPED_CHEST_1,
        MOVING_BOOKS,
        MOVING_KITS,
        DUPING_1,
        SNEAKING,
        RETRIEVING_KITS,
        SPREADING_BOOKS_1,
        STORING_KITS_1,
        NEARER_CENTER,
        OPENING_TRAPPED_CHEST_2,
        STORING_KITS_2,
        SPREADING_BOOKS_2,
        END_1,
        END_2,
        REPLENISHING_SELF_0,
        REPLENISHING_SELF_2,
        PUNCH_GRASS,
        MOVING_DUCK_KIT_SLOT,
        PLACING_DUCK_KIT,
        MOVING_CHESTS_TO_HOTBAR, MOVING_TRAPPED_CHESTS_TO_HOTBAR,
    }

    private static final int DUPE_DISCONNECT_INTERVAL_MILLIS = 20 * 1000;
    public int spiralProgress;
    private File spiralProgressFile;
    public State state;
    private IBaritone baritone;
    private static long ticks;
    private Queue<Pair<BlockPos, Block>> blocksToBePlaced;
    private BlockPos stashCenter;
    private static int currentDoubleChestBulkCount = 0;
    private List<BlockPos> chestsLeft;
    private List<String> kitsLeft;
    private long delay;
    public static Long askedChickenBot;
    private static long ticksNextChickenBotRequest;
    public static StashMakerModule INSTANCE;
    public static boolean stop;

    private static int POINT_SPACING_ORDER = 1;
    private static int DISTANCE_FROM_CENTER_ORDER = 1;
    private static int EXPANSION_RATE_ORDER = 1;

    static {
        INSTANCE = new StashMakerModule();
    }

    private StashMakerModule() {
        spiralProgressFile = FOLDER.toPath().resolve("spiral-progress.txt").toFile();
        readSpiralConstants();
        readSpiralProgress();
    }

    @Override
    public void onTick() {
        if (state == null || stop)
            return;
        if (--delay > 0)
            return;
        if (askedChickenBot != null && System.currentTimeMillis() - askedChickenBot > 30000) {
            askedChickenBot = null;
        }
        ticks++;
        switch (state) {
            case SHOULD_PATH -> {
                BaritoneAPI.getSettings().allowBreak.value = true;
                GoalXZ spiralPoint = computeNthSpiralPoint(spiralProgress);
                baritone.getCustomGoalProcess().setGoalAndPath(spiralPoint);
                state = PATHING_SPIRAL_POINT;
                Log.info("Pathing to spiral point " + spiralProgress + " at " + spiralPoint);
            }
            case PATHING_SPIRAL_POINT -> {
                Goal goal = baritone.getCustomGoalProcess().getGoal();
                if (goal == null) {
                    stashCenter = findStashRegion();
                    if (stashCenter == null) {
                        Log.info("Failed stash spiral point of index: " + spiralProgress);
                        spiralProgress++;
                        saveSpiralProgress();
                        state = SHOULD_PATH;
                        return;
                    }
                    BaritoneAPI.getSettings().allowBreak.value = false;
                    baritone.getCustomGoalProcess().setGoalAndPath(new GoalNear(stashCenter, 1));
                    Log.info("Pathing to stash platform for spiral point " + spiralProgress);
                    state = PATH_STASH_PLATFORM;
                    delay = 20;
                }
            }
            case PATH_STASH_PLATFORM -> {
                Goal goal = baritone.getCustomGoalProcess().getGoal();
                if (goal != null) {
                    return;
                }
                if (!mc.player.getBlockPos().isWithinDistance(stashCenter, 3)) {
                    spiralProgress++;
                    saveSpiralProgress();
                    state = SHOULD_PATH;
                    return;
                }
                Log.info("Sneaking 1");
                mc.options.sneakKey.setPressed(true);
                state = PUNCH_GRASS;
            }
            case PUNCH_GRASS -> {
                List<BlockPos> plantBlocks = BlockUtils.findBlocksNearby(mc.player.getBlockPos(), 3, BlockUtils.PLANTS);
                if (!plantBlocks.isEmpty()) {
                    BlockUtils.breakBlock(plantBlocks.get(0), true);
                } else {
                    state = NEARER_CENTER;
                    ticksStartNearerCenter = ticks;
                }
                delay = 1;
            }
            case NEARER_CENTER -> {
                if (ticks - ticksStartNearerCenter > 100) {
                    mc.options.jumpKey.setPressed(true);
                    ticksStartNearerCenter = 0;
                    return;
                }
                mc.player.lookAt(EntityAnchor.EYES, stashCenter.toCenterPos());
                double distanceToCenter = mc.player.getPos().distanceTo(stashCenter.toCenterPos());
                Log.info("Distance to center is " + distanceToCenter);
                if (!mc.player.getPos().isInRange(stashCenter.toCenterPos(), 0.51)) {
                    mc.options.sneakKey.setPressed(true);
                    mc.options.forwardKey.setPressed(true);
                } else {
                    mc.options.sneakKey.setPressed(false);
                    mc.options.forwardKey.setPressed(false);
                    Log.info("Building room 1");
                    state = MOVING_CHESTS_TO_HOTBAR;
                }
                mc.options.jumpKey.setPressed(false);
            }
            case MOVING_CHESTS_TO_HOTBAR -> {
                SwapModule.moving = CHEST;
                delay = 20;
            }
            case MOVING_TRAPPED_CHESTS_TO_HOTBAR -> {
                SwapModule.moving = TRAPPED_CHEST;
                delay = 20;
            }
            case BUILDING_ROOM_1 -> {
                if (mc.currentScreen instanceof GameMenuScreen || mc.currentScreen instanceof ChatScreen)
                    mc.currentScreen.close();
                baritone.getBuilderProcess().build("ministash5.schem", stashCenter.add(-1, 1, -1));
                state = SNEAKING;
                delay = 40;
            }
            case SNEAKING -> {
                Log.info("Sneaking 2");
                mc.options.sneakKey.setPressed(true);
                blocksToBePlaced.addAll(getStashModel(stashCenter));
                delay = 20;
                state = BUILDING_ROOM_2;
            }
            case BUILDING_ROOM_2 -> {
                if (!blocksToBePlaced.isEmpty()) {
                    Pair<BlockPos, Block> blockEntry = blocksToBePlaced.peek();
                    BlockPos blockPos = blockEntry.getLeft();
                    Log.info("Placing at " + blockPos.toShortString());
                    Block block = blockEntry.getRight();
                    Predicate<ItemStack> itemPredicate = (itemStack) -> itemStack.getItem() == block.asItem();
                    delay = 5;
                    boolean placedMaybe = BlockUtils.place(blockPos, itemPredicate);
                    blocksToBePlaced.remove(blockEntry);
                } else {
                    mc.options.sneakKey.setPressed(false);
                    Log.info("Built room");
                    baritone.getCustomGoalProcess().setGoalAndPath(new GoalNear(stashCenter, 1));
                    Log.info("Pathing to platform edge: " + stashCenter);
                    state = OPENING_TRAPPED_CHEST_1;
                    delay = 5;
                    chestsLeft = new ArrayList<>(
                            List.of(
                                    stashCenter.add(1, 2, -1),
                                    stashCenter.add(-1, 2, 0),
                                    stashCenter.add(1, 1, -1),
                                    stashCenter.add(-1, 1, 0)
                            )
                    );
                    ticksNextChickenBotRequest = 0;
                }
            }
            case OPENING_TRAPPED_CHEST_1 -> {
                if (!assertChestsPlaced()) {
                    state = BUILDING_ROOM_2;
                    return;
                }
                if (InvUtils.hasContainerOpened()) {
                    Log.info("Container opened. Moving books");
                    state = MOVING_BOOKS;
                    return;
                }
                BlockPos trappedChest = stashCenter.add(1, 1, 1);
                Log.info("Opening trapped chest 0");
                BlockUtils.interact(trappedChest);
                state = MOVING_BOOKS;
                delay = 20;
                int totalBooks = InvUtils.sumItemStacks(WRITTEN_BOOK);
                if (totalBooks != 27) {
                    Log.error("Weird amount of books: " + totalBooks);
                    state = null;
                    return;
                }
            }
            case MOVING_BOOKS -> {
                if (!InvUtils.hasContainerOpened()) {
                    Log.warn("Screen didn't open in time");
                    state = OPENING_TRAPPED_CHEST_1;
                    delay = 20;
                    return;
                }
                if (InvUtils.findInInventory(WRITTEN_BOOK).found()) {
                    InvUtils.quickMove().slot(InvUtils.findInInventory(WRITTEN_BOOK).slot());
                } else {
                    state = MOVING_KITS;
                    Log.info("Started moving kits to dupe");
                    kitsLeft = new ArrayList<>(getKitList());
                }
            }
            case MOVING_KITS -> {
                if (InvUtils.findInInventory(WRITTEN_BOOK).found()) {
                    state = MOVING_BOOKS;
                    return;
                }
                if (kitsLeft.isEmpty()) {
                    closeCurrentScreen();
                    Log.info("Started duping");
                    state = DUPING_1;
                    delay = 20;
                    return;
                }
                String kit = kitsLeft.remove(0);
                int slot = getKitSlotInInventory(kit);
                if (slot == -1) {
                    Log.error("Lacking " + kit + " kit");
                    state = null;
                } else {
                    InvUtils.quickMove().slot(slot);
                    delay = 2;
                }
            }
            case DUPING_1 -> {
                Log.info("Disconnecting");
                MC.disconnect();
                state = null;
                kitsLeft = new ArrayList<>(getKitList());
                new Thread(() -> {
                    long disconnectTime = System.currentTimeMillis();
                    while (true) {
                        if (System.currentTimeMillis() - disconnectTime >= DUPE_DISCONNECT_INTERVAL_MILLIS) {
                            Log.info("Reconnecting");
                            MC.connect();
                            delay = 40;
                            state = OPENING_TRAPPED_CHEST_2;
                            break;
                        }
                    }
                }).start();
            }
            case OPENING_TRAPPED_CHEST_2 -> {
                BlockPos trappedChest = stashCenter.add(1, 1, 1);
                Log.info("Opening trapped chest 1");
                BlockUtils.interact(trappedChest);
                delay = 30;
                state = RETRIEVING_KITS;
            }
            case RETRIEVING_KITS -> {
                if (kitsLeft.isEmpty()) {
                    Log.info("Done retrieving kits");
                    state = SPREADING_BOOKS_1;
                } else {
                    String kit = kitsLeft.remove(0);
                    Log.info("Retrieving " + kit);
                    if (!InvUtils.hasContainerOpened()) {
                        Log.info("No screen found");
                        state = OPENING_TRAPPED_CHEST_2;
                        return;
                    }
                    List<ItemStack> kitsMatchingInsideChest = InvUtils.getContainerStacks(itemStack -> InvUtils.nbtContains(itemStack, kit));
                    if (kitsMatchingInsideChest.size() == 0) {
                        Log.error("Didn't find kit inside chest. This shouldn't happen");
                        state = null;
                    } else if (kitsMatchingInsideChest.size() == 1) {
                        int slotId = InvUtils.getSlotIdWithStackInContainer(kitsMatchingInsideChest.get(0));
                        InvUtils.quickMove().slotId(slotId);
                    } else {
                        Log.error("Found duplicate kits in the dupe chest. This shouldn't happen");
                        state = null;
                    }
                    delay = 3;
                }
            }
            case SPREADING_BOOKS_1 -> {
                int bookCount = InvUtils.sumItemStacks(InvUtils.getContainerStacks(WRITTEN_BOOK));
                Log.info("Spreading books!! Book count: " + bookCount);
                ItemStack cursorStack = InvUtils.cursorStack();
                Log.info("cursorStack = " + cursorStack.toString());
                delay = 10;
                if (isChestFullOBooks() && cursorStack.getItem() != WRITTEN_BOOK) {
                    state = SPREADING_BOOKS_2;
                    delay = 20;
                    return;
                }
                if (cursorStack == ItemStack.EMPTY || cursorStack.getItem() == AIR) {
                    List<ItemStack> writtenBooks = InvUtils.getContainerStacks(itemStack -> itemStack.getCount() > 1 && itemStack.getItem() == WRITTEN_BOOK);
                    if (writtenBooks.isEmpty()) {
                        Log.error("I can't spread books, they are lacking!");
                        stop = true;
                    } else {
                        int bookSlot = InvUtils.getSlotWithStackInContainer(writtenBooks.get(0));
                        Log.info("Clicking " + bookSlot);
                        mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, bookSlot, 0, PICKUP, mc.player);
                    }
                    return;
                }
                if (cursorStack.getItem() == WRITTEN_BOOK) {
                    int emptySlot = InvUtils.getSlotWithStackInContainer(InvUtils.getContainerStacks(ItemStack::isEmpty).get(0));
                    Log.info("Right-clicking " + emptySlot);
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, emptySlot, 1, PICKUP, mc.player);
                    return;
                }
            }
            case SPREADING_BOOKS_2 -> {
                if (!isChestFullOBooks()) {
                    state = SPREADING_BOOKS_1;
                    return;
                }
                Log.info("Disconnecting 2");
                MC.disconnect();
                state = null;
                new Thread(() -> {
                    long disconnectTime = System.currentTimeMillis();
                    while (true) {
                        if (System.currentTimeMillis() - disconnectTime >= DUPE_DISCONNECT_INTERVAL_MILLIS) {
                            Log.info("Reconnecting");
                            state = STORING_KITS_1;
                            MC.connect();
                            break;
                        }
                    }
                }).start();
            }
            case STORING_KITS_1 -> {
                if (chestsLeft.isEmpty()) {
                    state = END_1;
                    return;
                }
                BlockPos storeChest = chestsLeft.get(0);
                Log.info("Opening chest left: " + chestsLeft.get(0).toShortString());
                BlockUtils.interact(storeChest);
                kitsLeft = new ArrayList<>(getKitList());
                state = STORING_KITS_2;
                delay = 30;
            }
            case STORING_KITS_2 -> {
                if (!InvUtils.hasContainerOpened()) {
                    Log.info("No screen found");
                    state = STORING_KITS_1;
                    return;
                }
                if (kitsLeft.isEmpty()) {
                    currentDoubleChestBulkCount++;
                    if (currentDoubleChestBulkCount == 3) {
                        currentDoubleChestBulkCount = 0;
                        if (chestsLeft.isEmpty()) {
                            Log.info("Yay I'm done lemme grab the dupe books");
                            closeCurrentScreen();
                            state = END_1;
                            delay = 1;
                            return;
                        }
                        chestsLeft.remove(0);
                    }
                    Log.info("Done storing kits. Duping again");
                    kitsLeft = new ArrayList<>(getKitList());
                    state = DUPING_1;
                } else {
                    String kit = kitsLeft.remove(0);
                    Log.info("Storing " + kit + " kit");
                    int slotId = getKitSlotIdInInventory(kit);
                    if (slotId == -1) {
                        Log.error("Lacking " + kit + " kit");
                        state = null;
                    } else {
                        InvUtils.quickMove().slotId(slotId);
                        delay = 1;
                    }
                }
            }
            case END_1 -> {
                BlockPos trappedChest = stashCenter.add(1, 1, 1);
                Log.info("Opening trapped chest 2");
                BlockUtils.interact(trappedChest);
                AutoDropModule.enabled = true;
                delay = 40;
                state = END_2;
            }
            case END_2 -> {
                if (InvUtils.sumItemStacks(WRITTEN_BOOK) == 27) {
                    Log.info("Done with current stash: " + spiralProgress);
                    Log.info("Checking requirements");
                    state = CHECKING_REQUIREMENTS;
                    return;
                }
                List<ItemStack> bookStacks = InvUtils.getContainerStacks(WRITTEN_BOOK);
                int slotId = InvUtils.getSlotIdWithStackInContainer(bookStacks.get(0));
                InvUtils.quickMove().slotId(slotId);
                delay = 1;
            }
            case CHECKING_REQUIREMENTS -> {
                boolean isReplenished = hasRequiredItems();
                if (!isReplenished) {
                    state = REPLENISHING_SELF_0;
                } else {
                    Log.info("Replenished! Pathing to next spot");
                    state = SHOULD_PATH;
                    spiralProgress++;
                    saveSpiralProgress();
                }
            }
            case REPLENISHING_SELF_0 -> {
                if (isChickenBotOnline()) {
                    if (askedChickenBot == null && ticks > ticksNextChickenBotRequest) {
                        Chat.send("/w _Robot_Chicken kit duck");
                        askedChickenBot = System.currentTimeMillis();
                    }
                }
                if (hasDuckKit()) {
                    Log.info("Got duck kit");
                    Predicate<ItemStack> duckPredicate = itemStack -> InvUtils.nbtContains(itemStack, "Duck");
                    if (!InvUtils.findInHotbar(duckPredicate).found()) {
                        ticksStartMovingDuckKit = ticks;
                        state = MOVING_DUCK_KIT_SLOT;
                    } else {
                        state = PLACING_DUCK_KIT;
                    }
                    AutoDropModule.enabled = false;
                    delay = 5;
                }
            }
            case MOVING_DUCK_KIT_SLOT -> {
                if (ticks - ticksStartMovingDuckKit == 5) {
                    placeSlot = InvUtils.findInInventory(stack -> InvUtils.nbtContains(stack, "Duck")).slot();
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, SlotUtils.indexToId(placeSlot), 0, SlotActionType.PICKUP, mc.player);
                } else if (ticks - ticksStartMovingDuckKit == 10) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, SlotUtils.indexToId(SlotUtils.HOTBAR_START + 5), 0, SlotActionType.PICKUP, mc.player);
                } else if (ticks - ticksStartMovingDuckKit == 15) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, SlotUtils.indexToId(placeSlot), 0, SlotActionType.PICKUP, mc.player);
                } else if (ticks - ticksStartMovingDuckKit > 20) {
                    state = PLACING_DUCK_KIT;
                }
            }
            case PLACING_DUCK_KIT -> {
                BlockPos duckKitPosition = stashCenter.add(-1, 1, -1);
                boolean placed = BlockUtils.place(duckKitPosition, duckKit());
                if (placed) {
                    Log.info("Placed duck kit at " + duckKitPosition.toShortString());
                    state = REPLENISHING_SELF_1;
                    delay = 20;
                } else {
                    Log.info("Failed to place duck kit at " + duckKitPosition.toShortString());
                    Log.info("Trying again");
                }
            }
            case REPLENISHING_SELF_1 -> {
                Log.info("Grabbing needed items");
                BlockPos duckKitPosition = stashCenter.add(-1, 1, -1);
                BlockUtils.interact(duckKitPosition);
                state = REPLENISHING_SELF_2;
                delay = 20;
            }
            case REPLENISHING_SELF_2 -> {
                Log.info("Retrieving needed");
                boolean replenished = retrieveNeeded();
                if (replenished) {
                    Log.info("Replenished! Pathing to next spot");
                    state = SHOULD_PATH;
                    spiralProgress++;
                    saveSpiralProgress();
                } else {
                    delay = 2;
                }
            }
        }
    }

    private boolean regionHasChestNear(BlockPos stashCenter) {
        return !BlockUtils.findBlocksNearby(stashCenter, 5, Blocks.CHEST, Blocks.TRAPPED_CHEST).isEmpty();
    }

    private boolean retrieveNeeded() {
        boolean hasChests = InvUtils.findInInventory(itemStack -> itemStack.getItem() == CHEST && itemStack.getCount() > 16).found();
        boolean hasTrappedChest = InvUtils.findInInventory(itemStack -> itemStack.getItem() == TRAPPED_CHEST).found();
        boolean hasGaps = InvUtils.findInInventory(itemStack -> itemStack.getItem() == Items.ENCHANTED_GOLDEN_APPLE && itemStack.getCount() > 16).found();
//        boolean hasPick = InvUtils.findInInventory(itemStack -> itemStack.getItem() == Items.DIAMOND_PICKAXE).found();
//        boolean hasShovel = InvUtils.findInInventory(itemStack -> itemStack.getItem() == Items.DIAMOND_SHOVEL).found();
//        boolean hasAxe = InvUtils.findInInventory(itemStack -> itemStack.getItem() == Items.DIAMOND_AXE).found();
//        boolean hasTotem = InvUtils.findInInventory(itemStack -> itemStack.getItem() == Items.TOTEM_OF_UNDYING).found();
        boolean hasBooks = hasBooks();
        if (!hasChests) {
            int slotId = InvUtils.getSlotIdWithStackInContainer(InvUtils.getContainerStacks(CHEST).get(0));
            InvUtils.quickMove().slotId(slotId);
            return hasTrappedChest && hasGaps && hasBooks;
        }
        if (!hasTrappedChest) {
            int slotId = InvUtils.getSlotIdWithStackInContainer(InvUtils.getContainerStacks(TRAPPED_CHEST).get(0));
            InvUtils.quickMove().slotId(slotId);
            return hasGaps && hasBooks;
        }
        if (!hasGaps) {
            int slotId = InvUtils.getSlotIdWithStackInContainer(InvUtils.getContainerStacks(ENCHANTED_GOLDEN_APPLE).get(0));
            InvUtils.quickMove().slotId(slotId);
            return hasBooks;
        }
        return hasBooks;
//        if (hasTotem) {
//            int slotId = InvUtils.getSlotIdWithStackInContainer(InvUtils.getContainerStacks(TOTEM_OF_UNDYING).get(0));
//            InvUtils.quickMove().slotId(slotId);
//            return hasBooks;
//        }
//        if(hasBooks) {
//
//        }
    }

    private boolean isChestFullOBooks() {
        for (int i = 0; i < 27; i++) {
            if (mc.player == null || mc.player.currentScreenHandler == null)
                return false;
            Slot slot = mc.player.currentScreenHandler.getSlot(i);
            ItemStack stack = slot.getStack();
            if (stack.isEmpty() || stack.getItem() != WRITTEN_BOOK || stack.getCount() != 1) {
                return false;
            }
        }
        return true;
    }

    private boolean assertChestsPlaced() {
        List<Pair<BlockPos, Block>> stashModel = getStashModel(stashCenter);
        for (Pair<BlockPos, Block> blockAt : stashModel) {
            BlockPos pos = blockAt.getLeft();
            Block block = blockAt.getRight();
            if (mc.world.getBlockState(pos).getBlock() != block) {
                return false;
            }
        }
        return true;
    }

    public static void setTicksNextChickenBotRequest() {
        ticksNextChickenBotRequest = ticks + 300;
    }

    private void closeCurrentScreen() {
        if (InvUtils.hasContainerOpened()) {
            Log.info("Closing screen");
            mc.currentScreen.close();
        } else {
            Log.info("Screen was already closed");
        }
    }

    private boolean isLookingAtRobotChicken() {
        return mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity().getDisplayName().getString().equals("_Robot_Chicken");
    }

    private boolean isChickenBotOnline() {
        for (PlayerListEntry playerListEntry : mc.getNetworkHandler().getPlayerList()) {
            if (playerListEntry.getProfile().getName().equals("_Robot_Chicken")) {
                return true;
            }
        }
        return false;
    }

    private int getKitSlotIdInInventory(String kit) {
        return SlotUtils.indexToId(InvUtils.findInInventory(stack -> InvUtils.nbtContains(stack, kit)).slot());
    }

    private int getKitSlotInInventory(String kit) {
        return InvUtils.findInInventory(stack -> InvUtils.nbtContains(stack, kit)).slot();
    }

    public static List<String> getKitList() {
        return Arrays.asList(
                "§cantonymph's glAss§r",
                "§5Elytra§r",
                "Life-Savers",
                "Terracotta™",
                "antonymph\\'s concrete",
                "CustomName:\"§6Chicken Food§r",
                "§cNether Chicken's Battle Box V4§r",
                "Wool™ ",
                "§cRedstone Kit§r",

                "Dyes & Organics",
                "antonymph's random building blocks",
                "Let there be light",
                "Sponge",
                "XP",
                "antonymph's trees n stuff kit <3",
                "Nether Kit",
                "antonymph's glazed terracotta",
                "_Nether_Chicken's Brewing"
        );
    }

    private boolean hasTwoBulksOfKits() {
        List<String> kitList = new ArrayList<>(getKitList());
        kitList.addAll(getKitList());
        kek:
        while (!kitList.isEmpty()) {
            String kit = kitList.get(0);
            for (int i = 0; i < mc.player.getInventory().size(); i++) {
                ItemStack stack = mc.player.getInventory().getStack(i);
                if (InvUtils.SHULKERS.contains(stack.getItem())) {
                    String nbtData = new StringNbtWriter().apply(stack.getNbt());
                    if (nbtData.contains(kit)) {
                        kitList.remove(kit);
                        continue kek;
                    }
                }
            }
            return false;
        }
        return true;
    }

    private void depositUneededItems() {
        List<ItemStack> itemStacks = InvUtils.findAllInInventory(itemStack ->
                !List.of(
                        CHEST,
                        TRAPPED_CHEST,
                        WRITTEN_BOOK,
                        Items.ENDER_CHEST,
                        Items.DIAMOND_PICKAXE,
                        Items.DIAMOND_SHOVEL,
                        Items.DIAMOND_AXE,
                        Items.ENCHANTED_GOLDEN_APPLE,
                        WRITTEN_BOOK,
                        Items.DIAMOND_HELMET,
                        Items.DIAMOND_CHESTPLATE,
                        Items.DIAMOND_LEGGINGS,
                        Items.DIAMOND_BOOTS,
                        Items.TOTEM_OF_UNDYING
                ).contains(itemStack.getItem()) && !InvUtils.SHULKERS.contains(itemStack.getItem()));
        new Thread(() -> InvUtils.putShulkerItems(itemStacks)).start();
    }

    private void grabNeededItems() {
        boolean lackingEC = InvUtils.findAllInInventory(itemStack -> itemStack.getItem() == Items.ENDER_CHEST).isEmpty();
        if (lackingEC) {
            InvUtils.getContainerStacks(itemStack -> itemStack.getItem() == Items.ENDER_CHEST);
        }
        List<ItemStack> chestStacks = InvUtils.findAllInInventory(itemStack -> itemStack.getItem() == CHEST);
        if (InvUtils.sumItemStacks(chestStacks) < 8) {
            InvUtils.getContainerStacks(itemStack -> itemStack.getItem() == CHEST);
        }
        List<ItemStack> totemStacks = InvUtils.findAllInInventory(itemStack -> itemStack.getItem() == Items.TOTEM_OF_UNDYING);
        if (totemStacks.isEmpty()) {
            InvUtils.getContainerStacks(itemStack -> itemStack.getItem() == Items.TOTEM_OF_UNDYING);
        }
        // ???
    }

    public void start() {
        mc.options.sneakKey.setPressed(false);
//        mc.player.networkHandler.sendPacket(new ClientCommandC2SPacket(mc.player, Mode.RELEASE_SHIFT_KEY));
        state = SHOULD_PATH;
        baritone = BaritoneAPI.getProvider().getPrimaryBaritone();
        baritone.getCustomGoalProcess().setGoal(null);
        ticks = 0;
        blocksToBePlaced = new ConcurrentLinkedDeque<>();
        stop = false;
    }

    private Predicate<ItemStack> duckKit() {
        return stack -> stack.getItem() == Items.LIGHT_BLUE_SHULKER_BOX && new StringNbtWriter().apply(stack.getNbt()).contains("Duck");
    }

    private boolean hasDuckKit() {
        return InvUtils.findInInventory(duckKit()).found();
    }

    private boolean hasRequiredItems() {
        boolean hasChests = InvUtils.findInInventory(itemStack -> itemStack.getItem() == CHEST && itemStack.getCount() > 16).found();
        boolean hasEnderChest = InvUtils.findInInventory(itemStack -> itemStack.getItem() == Items.ENDER_CHEST).found();
//        boolean hasBed = InvUtils.findInInventory(itemStack -> itemStack.getItem() == Items.RED_BED).found();
        boolean hasGaps = InvUtils.findInInventory(itemStack -> itemStack.getItem() == Items.ENCHANTED_GOLDEN_APPLE && itemStack.getCount() > 16).found();
        boolean hasPick = InvUtils.findInInventory(itemStack -> itemStack.getItem() == Items.DIAMOND_PICKAXE).found();
        boolean hasShovel = InvUtils.findInInventory(itemStack -> itemStack.getItem() == Items.DIAMOND_SHOVEL).found();
        boolean hasAxe = InvUtils.findInInventory(itemStack -> itemStack.getItem() == Items.DIAMOND_AXE).found();
        boolean hasTotem = InvUtils.findInInventory(itemStack -> itemStack.getItem() == Items.TOTEM_OF_UNDYING).found();
        boolean hasBooks = hasBooks();
        return hasChests && hasEnderChest && hasGaps && hasPick && hasShovel && hasAxe && hasTotem && hasBooks;
    }


    private boolean hasBooks() {
        return InvUtils.sumItemStacks(InvUtils.findAllInInventory(stack -> stack.getItem() == WRITTEN_BOOK)) == 27;
    }

    private void readSpiralConstants() {
        Properties properties = new Properties();
        FileInputStream input = null;
        try {
            input = new FileInputStream("stashmaker.properties");
            properties.load(input);
            POINT_SPACING_ORDER = Integer.parseInt(properties.getProperty("POINT_SPACING_ORDER"));
            DISTANCE_FROM_CENTER_ORDER = Integer.parseInt(properties.getProperty("DISTANCE_FROM_CENTER_ORDER"));
            EXPANSION_RATE_ORDER = Integer.parseInt(properties.getProperty("EXPANSION_RATE_ORDER"));
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void readSpiralProgress() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(spiralProgressFile));
            String line = reader.readLine();
            reader.close();
            spiralProgress = Integer.parseInt(line);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void saveSpiralProgress() {
        try {
            FileWriter fileWriter = new FileWriter(spiralProgressFile);
            PrintWriter printWriter = new PrintWriter(fileWriter);
            printWriter.println(spiralProgress);
            printWriter.close();
            fileWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<Pair<BlockPos, Block>> getStashModel(BlockPos stashCenter) {
        return List.of(
                new Pair(stashCenter.add(1, 2, 0), Blocks.CHEST),
                new Pair(stashCenter.add(1, 2, -1), Blocks.CHEST),

                new Pair(stashCenter.add(-1, 2, 0), Blocks.CHEST),
                new Pair(stashCenter.add(-1, 2, 1), Blocks.CHEST),
                new Pair(stashCenter.add(1, 2, 1), Blocks.TRAPPED_CHEST)
        );
    }

    private BlockPos findStashRegion() {
        Log.info("Finding stash region...");
        List<Block> invalidBlockTypes = List.of(Blocks.AIR, Blocks.WATER, Blocks.LAVA, Blocks.SAND, Blocks.SNOW);
        List<BlockPos> regions = new ArrayList<>();
        BlockPos position = mc.player.getBlockPos();
        int offset = 48;
        for (int y = offset; y > -offset; y--) {
            for (int x = -offset; x < offset; x++) {
                for (int z = -offset; z < offset; z++) {
                    boolean isRegion = true;
                    for (int dx = 0; dx < 3; dx++) {
                        for (int dy = 0; dy < 4; dy++) {
                            for (int dz = 0; dz < 3; dz++) {
                                BlockState state = mc.world.getBlockState(position.add(x + dx, y + dy, z + dz));
                                Block block = state.getBlock();
//                                if (dy == 0 && block != Blocks.RED_CONCRETE) {
                                if (dy == 0 && invalidBlockTypes.contains(block)) {
                                    isRegion = false;
                                    break;
                                }
                                if (dy > 0 && block != Blocks.AIR) {
                                    isRegion = false;
                                    break;
                                }
                            }
                            if (!isRegion)
                                break;
                        }
                        if (!isRegion)
                            break;
                    }
                    if (isRegion)
                        regions.add(position.add(x + 1, y, z + 1));
                }
            }
        }
        if (regions.isEmpty()) {
            Log.info("Failed to find a stash region");
            return null;
        } else {
            regions.sort(Comparator.comparingDouble(o -> o.getSquaredDistance(position)));
            return regions.stream().filter(Predicate.not(this::regionHasChestNear)).findFirst().orElse(null);
        }
    }

    public static GoalXZ computeNthSpiralPoint(int n) {
        final int POINT_SPACING = (int) Math.pow(2, POINT_SPACING_ORDER);
        final int DISTANCE_FROM_CENTER = (int) Math.pow(2, DISTANCE_FROM_CENTER_ORDER);
        final int EXPANSION_RATE = (int) Math.pow(2, EXPANSION_RATE_ORDER);

        double theta = ((double) POINT_SPACING * n) / DISTANCE_FROM_CENTER;
        int r = DISTANCE_FROM_CENTER + EXPANSION_RATE * (int) theta;
        int x = (int) Math.round(Math.cos(theta) * r);
        int z = (int) Math.round(Math.sin(theta) * r);
        return new GoalXZ(x, z);
    }
}
