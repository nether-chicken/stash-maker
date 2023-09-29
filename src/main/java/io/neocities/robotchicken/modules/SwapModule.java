package io.neocities.robotchicken.modules;

import io.neocities.robotchicken.util.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.item.*;
import net.minecraft.screen.slot.*;
import static io.neocities.robotchicken.general.MC.*;
import static net.minecraft.item.Items.*;

public class SwapModule extends Module {
    public static final SwapModule INSTANCE;

    static {
        INSTANCE = new SwapModule();
    }

    private int delay;
    private int step;
    public static Item moving;

    private static final Item[] IRREPLACEABLE_ITEMS = new Item[]{
            DIAMOND_PICKAXE,
            DIAMOND_AXE,
            DIAMOND_SHOVEL,
//            STONE,
            COBBLESTONE,
//            DIRT,
//            GRANITE,
//            ANDESITE,
//            DIORITE,
            ENCHANTED_GOLDEN_APPLE,
            TOTEM_OF_UNDYING,
            TRAPPED_CHEST,
            CHEST
    };

    private SwapModule() { }

    private boolean move() {
        switch (step) {
            case 0 -> {
                int slot = InvUtils.findInInventory(moving).slot();
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, SlotUtils.indexToId(slot), 0, SlotActionType.PICKUP, mc.player);
                if (InvUtils.findInHotbar(AIR).found()) {
                    mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, SlotUtils.indexToId(InvUtils.findInHotbar(AIR).slot()), 0, SlotActionType.PICKUP, mc.player);
                    reset();
                    return true;
                }
            }
            case 1 -> {
                int slot = InvUtils.findInHotbar(Predicate.not(stack -> Arrays.asList(IRREPLACEABLE_ITEMS).contains(stack.getItem()))).slot();
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, SlotUtils.indexToId(slot), 0, SlotActionType.PICKUP, mc.player);
            }
            case 2 -> {
                ItemStack cursorStack = InvUtils.cursorStack();
                if (cursorStack.getItem() == AIR || cursorStack.isEmpty()) {
                    reset();
                    return true;
                }
            }
            case 3 -> {
                int slot = InvUtils.findEmptyInInventory().slot();
                mc.interactionManager.clickSlot(mc.player.currentScreenHandler.syncId, SlotUtils.indexToId(slot), 0, SlotActionType.PICKUP, mc.player);
                reset();
                return true;
            }
        }
        return false;
    }

    @Override
    public void onTick() {
        if (moving == null)
            return;
        if (delay-- > 0)
            return;
        delay = 5;
        if (!move())
            step++;
    }

    private void reset() {
        moving = null;
        step = 0;
        delay = 0;
    }
}
