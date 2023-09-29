package io.neocities.robotchicken.modules;

import baritone.api.*;
import io.neocities.robotchicken.util.*;
import io.neocities.robotchicken.general.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.gui.screen.*;
import net.minecraft.item.*;
import static io.neocities.robotchicken.general.MC.*;
import static net.minecraft.item.Items.*;

public class AutoEatModule extends Module {

    private static final Item[] IRREPLACEABLE_ITEMS = new Item[]{
            DIAMOND_PICKAXE,
            DIAMOND_AXE,
            DIAMOND_SHOVEL,
            STONE,
            COBBLESTONE,
            DIRT
    };

    public static AutoEatModule INSTANCE;

    static {
        INSTANCE = new AutoEatModule();
    }

    private AutoEatModule() {
    }

    public boolean eating;
    public boolean movingToHotbar;
    private int slot, prevSlot;
    private boolean wasBaritone;

    @Override
    public void onTick() {
        if (eating) {
            if (shouldEat()) {
                if (mc.currentScreen instanceof GameMenuScreen || mc.currentScreen instanceof ChatScreen)
                    mc.currentScreen.close();
                if (!mc.player.getInventory().getStack(slot).isFood()) {
                    int slot = findSlot();
                    if (slot == -1) {
                        stopEating();
                        return;
                    } else {
                        changeSlot(slot);
                    }
                }
                eat();
            } else {
                stopEating();
            }
        } else {
            if (shouldEat()) {
                if (!InvUtils.findInHotbar(ENCHANTED_GOLDEN_APPLE).found()) {
                    InvUtils.move().from(InvUtils.findInInventory(ENCHANTED_GOLDEN_APPLE).slot()).toHotbar(InvUtils.findInHotbar(Predicate.not(item -> Arrays.asList(IRREPLACEABLE_ITEMS).contains(item))).slot());
                    return;
                }
                slot = findSlot();
                if (slot != -1)
                    startEating();
            }
        }
    }

    public void startEating() {
        prevSlot = mc.player.getInventory().selectedSlot;
        eat();
        wasBaritone = true;
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("pause");
    }

    public void eat() {
        if (!MC.inGame())
            return;
        changeSlot(slot);
        setPressed(true);
        eating = true;
    }

    private void stopEating() {
        changeSlot(prevSlot);
        setPressed(false);
        eating = false;
        wasBaritone = false;
        BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("resume");
    }

    private void setPressed(boolean pressed) {
        mc.options.useKey.setPressed(pressed);
    }

    private void changeSlot(int slot) {
        InvUtils.swap(slot, false);
        this.slot = slot;
    }

    private boolean shouldEat() {
        if (!MC.inGame())
            return false;
        return mc.player.getHungerManager().getFoodLevel() <= 16 || mc.player.getHealth() <= 10;
    }

    private int findSlot() {
        return InvUtils.findInHotbar(Items.ENCHANTED_GOLDEN_APPLE).slot();
    }
}
