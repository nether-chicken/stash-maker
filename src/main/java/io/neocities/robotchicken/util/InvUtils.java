/*
 * This file is part of the Meteor Client distribution (https://github.com/MeteorDevelopment/meteor-client).
 * Copyright (c) Meteor Development.
 */

package io.neocities.robotchicken.util;

import io.neocities.robotchicken.general.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.nbt.visitor.*;
import net.minecraft.network.packet.c2s.play.*;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.*;
import static io.neocities.robotchicken.general.MC.*;
import static net.minecraft.item.Items.*;
import static net.minecraft.item.Items.SHULKER_BOX;
import static net.minecraft.screen.ScreenHandlerType.*;

public class InvUtils {
    private static final Action ACTION = new Action();
    public static int previousSlot = -1;
    public static final List<Item> SHULKERS = List.of(
            SHULKER_BOX,
            WHITE_SHULKER_BOX,
            LIGHT_GRAY_SHULKER_BOX,
            GRAY_SHULKER_BOX,
            BLACK_SHULKER_BOX,
            BROWN_SHULKER_BOX,
            RED_SHULKER_BOX,
            ORANGE_SHULKER_BOX,
            YELLOW_SHULKER_BOX,
            LIME_SHULKER_BOX,
            GREEN_SHULKER_BOX,
            CYAN_SHULKER_BOX,
            LIGHT_BLUE_SHULKER_BOX,
            BLUE_SHULKER_BOX,
            PURPLE_SHULKER_BOX,
            MAGENTA_SHULKER_BOX,
            PINK_SHULKER_BOX
    );

    // Predicates
    public static boolean testInMainHand(Predicate<ItemStack> predicate) {
        return predicate.test(mc.player.getMainHandStack());
    }

    public static boolean testInMainHand(Item... items) {
        return testInMainHand(itemStack -> {
            for (var item : items) if (itemStack.isOf(item)) return true;
            return false;
        });
    }

    public static boolean testInOffHand(Predicate<ItemStack> predicate) {
        return predicate.test(mc.player.getOffHandStack());
    }

    public static boolean testInOffHand(Item... items) {
        return testInOffHand(itemStack -> {
            for (var item : items) if (itemStack.isOf(item)) return true;
            return false;
        });
    }

    public static boolean testInHands(Predicate<ItemStack> predicate) {
        return testInMainHand(predicate) || testInOffHand(predicate);
    }

    public static boolean testInHands(Item... items) {
        return testInMainHand(items) || testInOffHand(items);
    }

    public static boolean testInHotbar(Predicate<ItemStack> predicate) {
        if (testInHands(predicate)) return true;

        for (int i = SlotUtils.HOTBAR_START; i < SlotUtils.HOTBAR_END; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (predicate.test(stack)) return true;
        }

        return false;
    }

    public static boolean testInHotbar(Item... items) {
        return testInHotbar(itemStack -> {
            for (var item : items) if (itemStack.isOf(item)) return true;
            return false;
        });
    }

    public static int sumItemStacks(Item item) {
        return sumItemStacks(itemStack -> itemStack.getItem() == item);
    }

    public static int sumItemStacks(Predicate<ItemStack> isGood) {
        return sumItemStacks(InvUtils.findAllInInventory(isGood));
    }

    public static int sumItemStacks(Collection<ItemStack> itemStacks) {
        return itemStacks.stream().map(ItemStack::getCount).reduce(0, Integer::sum);
    }

    public static boolean nbtContains(ItemStack itemStack, String str) {
        if (!itemStack.hasNbt())
            return false;
        return new StringNbtWriter().apply(itemStack.getNbt()).contains(str);
    }

    public static ItemStack cursorStack() {
        return mc.player.currentScreenHandler.getCursorStack();
    }

    public static FindItemResult findEmptyInInventory() {
        return findInInventory(ItemStack::isEmpty);
    }

    public static FindItemResult findInHotbar(Item... items) {
        return findInHotbar(itemStack -> {
            for (Item item : items) {
                if (itemStack.getItem() == item) return true;
            }
            return false;
        });
    }

    public static FindItemResult findInHotbar(Predicate<ItemStack> isGood) {
        if (testInOffHand(isGood)) {
            return new FindItemResult(SlotUtils.OFFHAND, mc.player.getOffHandStack().getCount());
        }

        if (testInMainHand(isGood)) {
            return new FindItemResult(mc.player.getInventory().selectedSlot, mc.player.getMainHandStack().getCount());
        }

        return findInInventory(isGood, 0, 8);
    }

    public static FindItemResult findInInventory(Item... items) {
        return findInInventory(itemStack -> {
            for (Item item : items) {
                if (itemStack.getItem() == item) return true;
            }
            return false;
        });
    }

    public static FindItemResult findInInventory(Predicate<ItemStack> isGood) {
        if (mc.player == null) return new FindItemResult(0, 0);
        return findInInventory(isGood, 0, mc.player.getInventory().size());
    }

    public static void equipAll() {
        List.of(DIAMOND_HELMET, DIAMOND_CHESTPLATE, DIAMOND_LEGGINGS, DIAMOND_BOOTS, TOTEM_OF_UNDYING).forEach((item) -> {
            FindItemResult findItemResult = findInInventory(item);
            quickMove().slot(findItemResult.slot());
        });
    }

    public static boolean putInHand(Predicate<ItemStack> itemPredicate) {
        int slot = InvUtils.findInInventory(itemPredicate).slot();
        Log.info("Found item in " + slot);
        if (slot > 8) {
            InvUtils.move().from(slot).toId(mc.player.getInventory().selectedSlot);
            syncSelectedSlot();
            Log.info("Moved item from " + slot + " to " + 8);
            return true;
        }
        Log.info("Item is already in hotbar");
        return false;
    }

    public static List<ItemStack> findAllInInventory(Predicate<ItemStack> isGood) {
        List<ItemStack> matchingStacks = new ArrayList<>();
        for (int i = 0; i <= mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (isGood.test(stack))
                matchingStacks.add(stack);
        }
        return matchingStacks;
    }

    public static FindItemResult findInInventory(Predicate<ItemStack> isGood, int start, int end) {
        int slot = -1, count = 0;

        for (int i = start; i <= end; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);

            if (isGood.test(stack)) {
                if (slot == -1) slot = i;
                count += stack.getCount();
            }
        }

        return new FindItemResult(slot, count);
    }

    public static int getSlotIdWithStackInContainer(ItemStack itemStack) {
        for (int i = 0; i < mc.player.currentScreenHandler.slots.size(); i++) {
            Slot slot = mc.player.currentScreenHandler.getSlot(i);
            if (slot.getStack() == itemStack) {
                return slot.id;
            }
        }
        return -1;
    }

    public static int getSlotWithStackInContainer(ItemStack itemStack) {
        for (int i = 0; i < mc.player.currentScreenHandler.slots.size(); i++) {
            if (mc.player.currentScreenHandler.getSlot(i).getStack() == itemStack) {
                return i;
            }
        }
        return -1;
    }

    public static int getSlotWithStackInInventory(ItemStack itemStack) {
        return mc.player.getInventory().getSlotWithStack(itemStack);
    }

    public static void putShulkerItems(List<ItemStack> itemStacks) {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        for (ItemStack itemStack : itemStacks) {
            InvUtils.quickMove().from(mc.player.getInventory().getSlotWithStack(itemStack));
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static boolean hasContainerOpened() {
        if (mc.player.currentScreenHandler == null) {
            return false;
        }
        ScreenHandlerType type = null;
        try {
            type = mc.player.currentScreenHandler.getType();
            return type == GENERIC_9X3 || type == GENERIC_9X6;
        } catch (Exception e) {
            return false;
        }
    }

    public static List<ItemStack> getContainerStacks(Item... items) {
        return getContainerStacks(itemStack -> Arrays.asList(items).contains(itemStack.getItem()));
    }

    public static List<ItemStack> getContainerStacks(Predicate<ItemStack> isGood) {
        List<ItemStack> itemStacks = new ArrayList<>();
        for (Slot slot : mc.player.currentScreenHandler.slots) {
            if (!isGood.test(slot.getStack()))
                continue;
            itemStacks.add(slot.getStack());
        }
        return itemStacks;
    }

    public static FindItemResult findFastestTool(BlockState state) {
        float bestScore = 1;
        int slot = -1;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isSuitableFor(state)) continue;

            float score = stack.getMiningSpeedMultiplier(state);
            if (score > bestScore) {
                bestScore = score;
                slot = i;
            }
        }

        return new FindItemResult(slot, 1);
    }

    // Interactions

    public static boolean swap(int slot, boolean swapBack) {
        if (slot == SlotUtils.OFFHAND) return true;
        if (slot < 0 || slot > 8) return false;
        if (swapBack && previousSlot == -1) previousSlot = mc.player.getInventory().selectedSlot;
        else if (!swapBack) previousSlot = -1;

        mc.player.getInventory().selectedSlot = slot;
        syncSelectedSlot();
        return true;
    }

    public static void syncSelectedSlot() {
        int i = mc.player.getInventory().selectedSlot;
        if (i != previousSlot) {
            previousSlot = i;
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(previousSlot));
        }
    }

    public static boolean swapBack() {
        if (previousSlot == -1) return false;

        boolean return_ = swap(previousSlot, false);
        previousSlot = -1;
        return return_;
    }

    public static Action move() {
        ACTION.type = SlotActionType.PICKUP;
        ACTION.two = true;
        return ACTION;
    }

    public static Action click() {
        ACTION.type = SlotActionType.PICKUP;
        return ACTION;
    }

    public static Action rightClick() {
        ACTION.type = SlotActionType.PICKUP;
        ACTION.button = 1;
        return ACTION;
    }

    public static Action quickMove() {
        ACTION.type = SlotActionType.QUICK_MOVE;
        return ACTION;
    }

    public static Action drop() {
        ACTION.type = SlotActionType.THROW;
        ACTION.button = 1;
        return ACTION;
    }

    public static void dropHand() {
        if (!mc.player.currentScreenHandler.getCursorStack().isEmpty())
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, ScreenHandler.EMPTY_SPACE_SLOT_INDEX, 0, SlotActionType.PICKUP, mc.player);
    }

    public static class Action {
        private SlotActionType type = null;
        private boolean two = false;
        private int from = -1;
        private int to = -1;
        private int button = 0;

        private boolean isRecursive = false;

        private Action() {
        }

        // From

        public Action fromId(int id) {
            from = id;
            return this;
        }

        public Action from(int index) {
            return fromId(SlotUtils.indexToId(index));
        }

        public Action fromHotbar(int i) {
            return from(SlotUtils.HOTBAR_START + i);
        }

        public Action fromOffhand() {
            return from(SlotUtils.OFFHAND);
        }

        public Action fromMain(int i) {
            return from(SlotUtils.MAIN_START + i);
        }

        public Action fromArmor(int i) {
            return from(SlotUtils.ARMOR_START + (3 - i));
        }

        // To

        public void toId(int id) {
            to = id;
            run();
        }

        public void to(int index) {
            toId(SlotUtils.indexToId(index));
        }

        public void toHotbar(int i) {
            to(SlotUtils.HOTBAR_START + i);
        }

        public void toOffhand() {
            to(SlotUtils.OFFHAND);
        }

        public void toMain(int i) {
            to(SlotUtils.MAIN_START + i);
        }

        public void toArmor(int i) {
            to(SlotUtils.ARMOR_START + (3 - i));
        }

        // Slot

        public void slotId(int id) {
            from = to = id;
            run();
        }

        public void slot(int index) {
            slotId(SlotUtils.indexToId(index));
        }

        public void slotHotbar(int i) {
            slot(SlotUtils.HOTBAR_START + i);
        }

        public void slotOffhand() {
            slot(SlotUtils.OFFHAND);
        }

        public void slotMain(int i) {
            slot(SlotUtils.MAIN_START + i);
        }

        public void slotArmor(int i) {
            slot(SlotUtils.ARMOR_START + (3 - i));
        }

        // Other

        private void run() {
            boolean hadEmptyCursor = mc.player.currentScreenHandler.getCursorStack().isEmpty();

            if (type != null && from != -1 && to != -1) {
                click(from);
                if (two) click(to);
            }

            SlotActionType preType = type;
            boolean preTwo = two;
            int preFrom = from;
            int preTo = to;

            type = null;
            two = false;
            from = -1;
            to = -1;
            button = 0;

            if (!isRecursive && hadEmptyCursor && preType == SlotActionType.PICKUP && preTwo && (preFrom != -1 && preTo != -1) && !mc.player.currentScreenHandler.getCursorStack().isEmpty()) {
                isRecursive = true;
                InvUtils.click().slotId(preFrom);
                isRecursive = false;
            }
        }

        private void click(int id) {
            mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, id, button, type, mc.player);
        }
    }
}
