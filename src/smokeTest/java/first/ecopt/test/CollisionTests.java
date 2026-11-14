package first.ecopt.test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

import first.ecopt.EcoptClassFilters;
import first.ecopt.EcoptCollisions;
import first.ecopt.EcoptPush;
import first.ecopt.EcoptShapeList;
import first.ecopt.EcoptTicks;
import first.ecopt.duck.EcoptSectionLists;
import first.ecopt.duck.EcoptSectionStamp;
import first.ecopt.duck.SectionPosHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("ecopt_tests")
@PrefixGameTestTemplate(false)
public final class CollisionTests {
    @GameTest(template = "empty")
    public static void mappedClassFilters(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pig pig = EntityType.PIG.create(level);
        Boat boat = EntityType.BOAT.create(level);
        helper.assertTrue(!EcoptClassFilters.maybeHard(pig), "Pig must stay outside hard candidates");
        helper.assertTrue(EcoptClassFilters.maybePushable(pig), "Pig must be pushable");
        helper.assertTrue(EcoptClassFilters.maybeHard(boat), "Boat must be a hard candidate");
        helper.assertTrue(EcoptClassFilters.hasCustomCollision(boat), "Boat needs custom collision fallback");
        helper.assertTrue(!EcoptClassFilters.hasCustomCollision(pig), "Pig uses the optimized default rule");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void collisionDispatchAndBoatFallback(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(5, 3, 5));
        level.getChunkAt(pos);
        Pig pig = EntityType.PIG.create(level);
        pig.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        pig.setNoAi(true);
        pig.setNoGravity(true);
        level.addFreshEntity(pig);
        Boat boat = EntityType.BOAT.create(level);
        boat.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        // 不将查询船加入世界，查询框内只有非硬实体猪。
        helper.runAfterDelay(3, () -> {
            AABB box = pig.getBoundingBox();
            helper.assertTrue(level.getEntityCollisions(null, box).isEmpty(), "Null query must ignore pigs");
            helper.assertTrue(EcoptCollisions.query(level, boat, box) == null, "Custom rule must fall back");
            helper.assertTrue(!level.getEntityCollisions(boat, box).isEmpty(), "Boat must collide with pig via vanilla fallback");
            Boat obstacle = EntityType.BOAT.create(level);
            obstacle.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            obstacle.setNoGravity(true);
            level.addFreshEntity(obstacle);
            List<VoxelShape> shapes = level.getEntityCollisions(pig, box);
            helper.assertTrue(shapes instanceof EcoptShapeList && !shapes.isEmpty(), "Level must dispatch to lazy candidate query");
            helper.assertTrue(shapes.get(0).bounds().equals(obstacle.getBoundingBox()), "Lazy shape must match obstacle");
            obstacle.discard();
            helper.assertTrue(level.getEntityCollisions(pig, box).isEmpty(), "Removed obstacle must leave candidates");
            pig.discard();
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void pushCandidatesMatchVanilla(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(5, 3, 5));
        level.getChunkAt(pos);
        List<Pig> pigs = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Pig pig = EntityType.PIG.create(level);
            pig.setPos(pos.getX() + 0.5 + i * 0.05, pos.getY(), pos.getZ() + 0.5);
            pig.setNoAi(true);
            pig.setNoGravity(true);
            level.addFreshEntity(pig);
            pigs.add(pig);
        }
        helper.runAfterDelay(3, () -> {
            Pig except = pigs.get(0);
            AABB box = except.getBoundingBox().inflate(1);
            Predicate<Entity> predicate = EntitySelector.pushableBy(except);
            List<Entity> fast = EcoptPush.query(level, except, box, predicate);
            helper.assertTrue(fast != null && fast.size() == 3, "All other pigs must reach push candidates");
            helper.assertTrue(new HashSet<>(fast).equals(new HashSet<>(level.getEntities(except, box, predicate))), "Push query must match vanilla");
            pigs.forEach(pig -> pig.setDeltaMovement(Vec3.ZERO));
            Pig skipped = pigs.get(1);
            skipped.addTag("ecopt_tests_skip_push");
            int queries = TestMod.pushQueries;
            except.aiStep();
            helper.assertTrue(TestMod.pushQueries > queries, "Another wrapper at the push query must still run");
            helper.assertTrue(skipped.getDeltaMovement().equals(Vec3.ZERO), "The other wrapper must be able to exclude a push target");
            helper.assertTrue(pigs.get(2).getDeltaMovement().horizontalDistanceSqr() > 0, "Unfiltered targets must still be pushed");
            pigs.forEach(Entity::discard);
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void blockCacheAndFluidInvalidation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 3, 4));
        LevelChunk chunk = level.getChunkAt(pos);
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(pos.getY()));
        EcoptSectionStamp stamp = (EcoptSectionStamp) section;
        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        helper.assertTrue(chunk.getBlockState(pos).is(Blocks.STONE), "Cache must read stone");
        int before = stamp.ecopt$sectionStamp();
        level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        helper.assertTrue(stamp.ecopt$sectionStamp() != before, "Block writes must invalidate cache");
        helper.assertTrue(chunk.getBlockState(pos).is(Blocks.WATER), "Same tick cached state must refresh");
        helper.assertTrue(stamp.ecopt$hasFluid(level.getGameTime()), "Fluid palette must refresh in same tick");
        before = stamp.ecopt$sectionStamp();
        section.recalcBlockCounts();
        helper.assertTrue(stamp.ecopt$sectionStamp() != before, "Recount must invalidate palette");
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(chunk.getBlockState(pos).isAir(), "Cache must remain correct across ticks");
            helper.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void noCollisionMatchesVanilla(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(3, 3, 3));
        BlockState[] states = {Blocks.STONE.defaultBlockState(), Blocks.OAK_FENCE.defaultBlockState(),
                Blocks.STONE_SLAB.defaultBlockState(), Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP),
                Blocks.AIR.defaultBlockState(), Blocks.WATER.defaultBlockState()};
        for (int i = 0; i < states.length; i++) {
            level.setBlockAndUpdate(origin.offset(i, 0, 0), states[i]);
        }
        Random random = new Random(1201);
        for (int i = 0; i < 300; i++) {
            double x = origin.getX() - 1 + random.nextDouble() * 8;
            double y = origin.getY() - 0.5 + random.nextDouble() * 3;
            double z = origin.getZ() - 0.5 + random.nextDouble() * 2;
            AABB box = new AABB(x, y, z, x + 0.6, y + 0.8, z + 0.6);
            boolean expected = vanillaNoCollision(level, null, box);
            helper.assertTrue(level.noCollision(null, box) == expected, "noCollision differs from vanilla for sample " + i);
            helper.assertTrue(level.noCollision(box) == expected, "Single argument overload must dispatch to Level");
        }
        helper.succeed();
    }

    private static boolean vanillaNoCollision(ServerLevel level, Entity entity, AABB box) {
        for (VoxelShape shape : level.getBlockCollisions(entity, box)) {
            if (!shape.isEmpty()) return false;
        }
        Predicate<Entity> predicate = entity == null ? EntitySelector.CAN_BE_COLLIDED_WITH
                : EntitySelector.NO_SPECTATORS.and(entity::canCollideWith);
        if (!level.getEntities(entity, box.inflate(1.0E-7), predicate).isEmpty()) return false;
        return entity == null || !level.getWorldBorder().isInsideCloseToBorder(entity, box)
                || !Shapes.joinIsNotEmpty(level.getWorldBorder().getCollisionShape(), Shapes.create(box), BooleanOp.AND);
    }

    @GameTest(template = "empty")
    public static void sectionIndexAndCandidates(GameTestHelper helper) {
        EntitySection<Entity> section = new EntitySection<>(Entity.class, Visibility.TICKING);
        ((SectionPosHolder) section).ecopt$setSectionPos(SectionPos.asLong(0, 0, 0));
        Pig near = EntityType.PIG.create(helper.getLevel());
        near.setPos(4, 4, 4);
        Pig far = EntityType.PIG.create(helper.getLevel());
        far.setPos(12, 4, 12);
        section.add(near);
        section.add(far);
        EcoptTicks.bump();
        AABB query = new AABB(2, 2, 2, 6, 6, 6);
        for (int i = 0; i < 3; i++) {
            List<Entity> found = new ArrayList<>();
            section.getEntities(query, entity -> {
                found.add(entity);
                return AbortableIterationConsumer.Continuation.CONTINUE;
            });
            helper.assertTrue(found.equals(List.of(near)), "Linear and indexed results must agree without duplicates");
        }
        helper.assertTrue(((EcoptSectionLists) section).ecopt$pushables().size() == 2, "Incremental candidates must contain pigs");
        helper.assertTrue(((EcoptSectionLists) section).ecopt$hardEntities().isEmpty(), "Hard table must exclude pigs");
        helper.assertTrue(section.remove(near), "Removal must succeed");
        List<Entity> found = new ArrayList<>();
        section.getEntities(query, entity -> {
            found.add(entity);
            return AbortableIterationConsumer.Continuation.CONTINUE;
        });
        helper.assertTrue(found.isEmpty(), "Remove must invalidate subcell index immediately");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void supportingBlockAndFluidScan(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = helper.absolutePos(new BlockPos(5, 2, 5));
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        Pig pig = EntityType.PIG.create(level);
        pig.setPos(floor.getX() + 0.5, floor.getY() + 1, floor.getZ() + 0.5);
        double y = pig.getY();
        pig.move(MoverType.SELF, new Vec3(0, -0.08, 0));
        helper.assertTrue(pig.getY() == y, "Supporting block must stop vertical movement");
        pig.updateFluidHeightAndDoFluidPushing(state -> true);
        level.setBlockAndUpdate(floor.above(), Blocks.WATER.defaultBlockState());
        pig.updateFluidHeightAndDoFluidPushing(state -> true);
        helper.assertTrue(pig.isInWater() || pig.getFluidTypeHeight(net.minecraftforge.common.ForgeMod.WATER_TYPE.get()) > 0,
                "Wet palette must run real fluid scan");
        helper.succeed();
    }
}
