package io.neocities.robotchicken.util;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.util.*;
import net.minecraft.util.math.*;
import static io.neocities.robotchicken.general.MC.*;

public class SchematicScaffold {

    private final BlockPos.Mutable bp = new BlockPos.Mutable();
    private final BlockPos.Mutable prevBp = new BlockPos.Mutable();

    private boolean lastWasSneaking;
    private double placeRange = 5;
    private double radius = 5;
    private boolean rotate = true;
    private double lastSneakingY;

    public void onTick() {
        if (BlockUtils.getPlaceSide(mc.player.getBlockPos().down()) != null) {
            bp.set(mc.player.getBlockPos().down());
        } else {
            Vec3d pos = mc.player.getPos();
            pos = pos.add(0, -0.98f, 0);
            pos.add(mc.player.getVelocity());

            if (!PlayerUtils.isWithin(prevBp, placeRange)) {
                List<BlockPos> blockPosArray = new ArrayList<>();

                for (int x = (int) (mc.player.getX() - placeRange); x < mc.player.getX() + placeRange; x++) {
                    for (int z = (int) (mc.player.getZ() - placeRange); z < mc.player.getZ() + placeRange; z++) {
                        for (int y = (int) Math.max(mc.world.getBottomY(), mc.player.getY() - placeRange); y < Math.min(mc.world.getTopY(), mc.player.getY() + placeRange); y++) {
                            bp.set(x, y, z);
                            if (!mc.world.getBlockState(bp).isAir()) blockPosArray.add(new BlockPos(bp));
                        }
                    }
                }
                if (blockPosArray.size() == 0) {
                    return;
                }

                blockPosArray.sort(Comparator.comparingDouble(PlayerUtils::squaredDistanceTo));

                prevBp.set(blockPosArray.get(0));
            }

            Vec3d vecPrevBP = new Vec3d((double) prevBp.getX() + 0.5f,
                    (double) prevBp.getY() + 0.5f,
                    (double) prevBp.getZ() + 0.5f);

            Vec3d sub = pos.subtract(vecPrevBP);
            Direction facing;
            if (sub.getY() < -0.5f) {
                facing = Direction.DOWN;
            } else if (sub.getY() > 0.5f) {
                facing = Direction.UP;
            } else facing = Direction.getFacing(sub.getX(), 0, sub.getZ());

            bp.set(prevBp.offset(facing));
        }

        // Move down if shifting
        if (mc.options.sneakKey.isPressed() && !mc.options.jumpKey.isPressed()) {
            if (lastSneakingY - mc.player.getY() < 0.1) {
                lastWasSneaking = false;
                return;
            }
        } else {
            lastWasSneaking = false;
        }
        if (!lastWasSneaking) lastSneakingY = mc.player.getY();

        place(bp);
        if (!mc.world.getBlockState(bp).isAir()) {
            prevBp.set(bp);
        }
    }

    private boolean validItem(ItemStack itemStack, BlockPos pos) {
        if (!(itemStack.getItem() instanceof BlockItem)) return false;
        Block block = ((BlockItem) itemStack.getItem()).getBlock();
        if (!Block.isShapeFullCube(block.getDefaultState().getCollisionShape(mc.world, pos))) return false;
        return !(block instanceof FallingBlock) || !FallingBlock.canFallThrough(mc.world.getBlockState(pos));
    }

    private boolean place(BlockPos bp) {
        FindItemResult item = InvUtils.findInHotbar(itemStack -> validItem(itemStack, bp));
        Identifier id = mc.world.getRegistryManager().get(RegistryKeys.BIOME).getId(mc.world.getBiome(bp).value());
        if (!item.found()) return false;
        if (BlockUtils.place(bp, item, rotate, 50, false, true)) {
            return true;
        }
        return false;
    }
}
