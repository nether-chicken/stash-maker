package io.neocities.robotchicken.modules;

import baritone.api.*;
import io.neocities.robotchicken.util.*;
import io.neocities.robotchicken.general.*;
import java.util.*;
import net.minecraft.entity.*;
import net.minecraft.entity.mob.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import net.minecraft.world.*;
import static io.neocities.robotchicken.general.MC.*;

public class KillAura extends Module {

    private final List<Entity> targets = new ArrayList<>();
    private final boolean rotate = true;
    private final double range = 2.5;
    private boolean wasPathing = false;
    private int hitTimer = 0;
    public static final KillAura INSTANCE;

    static {
        INSTANCE = new KillAura();
    }

    private KillAura() { }

    @Override
    public void onTick() {
        if (!MC.inGame())
            return;
        if (!mc.player.isAlive() || PlayerUtils.getGameMode() == GameMode.SPECTATOR) return;
        if ((mc.interactionManager.isBreakingBlock() || mc.player.isUsingItem()))
            return;
        if (AutoEatModule.INSTANCE.eating) {
            return;
        }
        targets.clear();
        TargetUtils.getList(targets, this::entityCheck);

        if (targets.isEmpty()) {
            if (wasPathing) {
                BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("resume");
                wasPathing = false;
            }
            return;
        }

        Entity primary = targets.get(0);
        if (rotate)
            Rotations.rotate(Rotations.getYaw(primary), Rotations.getPitch(primary.getPos()));
        if (BaritoneAPI.getProvider().getPrimaryBaritone().getPathingBehavior().isPathing() && !wasPathing) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("pause");
            wasPathing = true;
        }
        targets.forEach(this::attack);
    }

    private void attack(Entity target) {
        if (hitTimer-- != 0)
            return;
        if (rotate)
            Rotations.rotate(Rotations.getYaw(target), Rotations.getPitch(target.getPos()));

        mc.interactionManager.attackEntity(mc.player, target);
        mc.player.swingHand(Hand.MAIN_HAND);

        hitTimer = 10;
    }

    private boolean entityCheck(Entity entity) {
        if (entity.equals(mc.player) || entity.equals(mc.cameraEntity)) return false;
        if ((entity instanceof LivingEntity && ((LivingEntity) entity).isDead()) || !entity.isAlive())
            return false;

        Box hitbox = entity.getBoundingBox();
        if (!PlayerUtils.isWithin(
                MathHelper.clamp(mc.player.getX(), hitbox.minX, hitbox.maxX),
                MathHelper.clamp(mc.player.getY(), hitbox.minY, hitbox.maxY),
                MathHelper.clamp(mc.player.getZ(), hitbox.minZ, hitbox.maxZ),
                range
        )) return false;
        return entity instanceof HostileEntity;
    }
}
