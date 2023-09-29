package io.neocities.robotchicken.modules;

import io.neocities.robotchicken.general.*;
import io.neocities.robotchicken.util.*;
import java.util.*;
import java.util.function.*;
import net.minecraft.client.gui.screen.ingame.*;
import net.minecraft.item.*;
import static io.neocities.robotchicken.general.MC.*;
import static net.minecraft.item.Items.*;

public class AutoDropModule extends Module {
    public static final AutoDropModule INSTANCE;
    public static boolean enabled = false;

    static {
        INSTANCE = new AutoDropModule();
    }

    private AutoDropModule() {

    }

    private Predicate<ItemStack> droppables() {
        return Predicate.not(
                itemStack -> Arrays.asList(
                        CHEST,
                        TRAPPED_CHEST,
                        TOTEM_OF_UNDYING,
                        DIAMOND_PICKAXE,
                        DIAMOND_AXE,
                        DIAMOND_SHOVEL,
                        ENCHANTED_GOLDEN_APPLE,
                        DIAMOND_HELMET,
                        DIAMOND_CHESTPLATE,
                        DIAMOND_LEGGINGS,
                        DIAMOND_BOOTS,
                        ELYTRA,
                        FIREWORK_ROCKET,
                        ENDER_CHEST,
                        WRITTEN_BOOK, RED_BED
                ).contains(itemStack.getItem())
                        ||
                        InvUtils.SHULKERS.contains(itemStack.getItem())
        );
    }

    @Override
    public void onTick() {
        Log.info("Dropping stuff");
        if (mc.currentScreen instanceof HandledScreen<?>) return;
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack itemStack = mc.player.getInventory().getStack(i);
            if (droppables().test(itemStack)) {
                InvUtils.drop().slot(i);
            }
        }
    }
}
