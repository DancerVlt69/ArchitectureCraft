/*
 * MIT License
 *
 * Copyright (c) 2017 Benjamin K
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.tridevmc.architecture.common.block;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.math.Vector3d;
import com.tridevmc.architecture.client.render.model.OBJSON;
import com.tridevmc.architecture.common.ArchitectureLog;
import com.tridevmc.architecture.common.ArchitectureMod;
import com.tridevmc.architecture.common.block.state.BlockStateArchitecture;
import com.tridevmc.architecture.common.helpers.Trans3;
import com.tridevmc.architecture.common.helpers.Vector3;
import com.tridevmc.architecture.common.render.ITextureConsumer;
import com.tridevmc.architecture.common.render.ModelSpec;
import com.tridevmc.architecture.common.utils.MiscUtils;
import com.tridevmc.compound.core.reflect.WrappedField;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.Material;
import net.minecraft.world.level.material.MaterialColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Objects;

public abstract class BlockArchitecture extends BaseEntityBlock implements ITextureConsumer {

    private static final WrappedField<StateDefinition<Block, BlockState>> STATE_CONTAINER = WrappedField.create(Block.class, "stateContainer", "field_176227_L");
    private static final LoadingCache<ShapeContext, VoxelShape> SHAPE_CACHE = CacheBuilder.newBuilder().build(new CacheLoader<ShapeContext, VoxelShape>() {
        public VoxelShape load(@Nonnull ShapeContext shapeContext) {
            VoxelShape shape = shapeContext.state.getBlock().getLocalBounds(shapeContext.world, shapeContext.pos, shapeContext.state, null);
            return shape;
        }
    });

    public static boolean debugState = false;
    // --------------------------- Orientation -------------------------------
    public static IOrientationHandler orient1Way = new Orient1Way();
    protected MaterialColor materialColor;
    protected Property[] stateProperties;
    // --------------------------- Members -------------------------------
    protected Object[][] propertyValues;
    protected int numProperties; // Do not explicitly initialise
    protected RenderShape renderID = RenderShape.MODEL;
    protected IOrientationHandler orientationHandler = orient1Way;
    protected String[] textureNames;
    protected ModelSpec modelSpec;


    // --------------------------- Constructors -------------------------------

    public BlockArchitecture(Material material) {
        this(material, null);
    }

    public BlockArchitecture(Material material, IOrientationHandler orient) {
        super(Block.Properties.of(material, material.getColor()).dynamicShape());
        if (orient == null)
            orient = orient1Way;
        this.orientationHandler = orient;
        var builder = new StateDefinition.Builder<Block, BlockState>(this);
        this.createBlockStateDefinition(builder);
        STATE_CONTAINER.set(this, builder.create(Block::defaultBlockState, (block, propertyValues, codec) -> new BlockStateArchitecture((BlockArchitecture) block, propertyValues, codec)));
        this.registerDefaultState(this.getStateDefinition().any());
    }

    public IOrientationHandler getOrientationHandler() {
        return orient1Way;
    }

    // --------------------------- States -------------------------------

    protected void defineProperties() {
        this.stateProperties = new Property[4];
        this.propertyValues = new Object[4][];
        this.getOrientationHandler().defineProperties(this);
    }

    public void addProperty(Property property) {
        if (debugState)
            ArchitectureLog.info("BaseBlock.addProperty: %s to %s\n", property, this.getClass().getName());
        if (this.numProperties < 4) {
            int i = this.numProperties++;
            this.stateProperties[i] = property;
            Object[] values = MiscUtils.arrayOf(property.getPossibleValues());
            this.propertyValues[i] = values;
        } else
            throw new IllegalStateException("Block " + this.getClass().getName() +
                    " has too many properties");
        if (debugState)
            ArchitectureLog.info("BaseBlock.addProperty: %s now has %s properties\n",
                    this.getClass().getName(), this.numProperties);
    }

    private void dumpProperties() {
        ArchitectureLog.info("BaseBlock: Properties of %s:\n", this.getClass().getName());
        for (int i = 0; i < this.numProperties; i++) {
            ArchitectureLog.info("%s: %s\n", i, this.stateProperties[i]);
            Object[] values = this.propertyValues[i];
            for (int j = 0; j < values.length; j++)
                ArchitectureLog.info("   %s: %s\n", j, values[j]);
        }
    }

    protected void checkProperties() {
        int n = 1;
        for (int i = 0; i < this.numProperties; i++)
            n *= this.propertyValues[i].length;
        if (n > 16)
            throw new IllegalStateException(String.format(
                    "Block %s has %s combinations of property values (16 allowed)",
                    this.getClass().getName(), n));
    }

    public int getNumSubtypes() {
        return 1;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        if (this.stateProperties == null) this.defineProperties();
        Arrays.stream(this.stateProperties).filter(Objects::nonNull).forEach(builder::add);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return this.renderID;
    }

    public String getQualifiedRendererClassName() {
        String name = this.getRendererClassName();
        if (name != null)
            name = this.getClass().getPackage().getName() + "." + name;
        return name;
    }

    // -------------------------- Rendering -----------------------------

    protected String getRendererClassName() {
        return null;
    }

    public void setModelAndTextures(String modelName, String... textureNames) {
        this.textureNames = textureNames;
        this.modelSpec = new ModelSpec(modelName, textureNames);
    }

    public void setModelAndTextures(String modelName, Vector3 origin, String... textureNames) {
        this.textureNames = textureNames;
        this.modelSpec = new ModelSpec(modelName, origin, textureNames);
    }

    public String[] getTextureNames() {
        return this.textureNames;
    }

    public ModelSpec getModelSpec(BlockState state) {
        return this.modelSpec;
    }

    public Trans3 localToGlobalRotation(BlockAndTintGetter world, BlockPos pos) {
        return this.localToGlobalRotation(world, pos, world.getBlockState(pos));
    }

    public Trans3 localToGlobalRotation(BlockAndTintGetter world, BlockPos pos, BlockState state) {
        return this.localToGlobalTransformation(world, pos, state, Vector3.zero);
    }

    public Trans3 localToGlobalTransformation(BlockAndTintGetter world, BlockPos pos) {
        return this.localToGlobalTransformation(world, pos, world.getBlockState(pos));
    }

    public Trans3 localToGlobalTransformation(BlockAndTintGetter world, BlockPos pos, BlockState state) {
        return this.localToGlobalTransformation(world, pos, state, Vector3.zero);
    }

    public Trans3 localToGlobalTransformation(BlockAndTintGetter world, BlockPos pos, BlockState state, Vector3 origin) {
        IOrientationHandler oh = this.getOrientationHandler();
        return oh.localToGlobalTransformation(world, pos, state, origin);
    }

    public Trans3 itemTransformation() {
        return Trans3.ident;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Vec3 hit = context.getClickLocation();
        BlockState state = this.getOrientationHandler().onBlockPlaced(this, context.getLevel(), context.getClickedPos(), context.getNearestLookingDirection(),
                hit.x(), hit.y(), hit.z(), this.defaultBlockState(), context.getPlayer());
        return state;
    }

    @Override
    public boolean addLandingEffects(BlockState state, ServerLevel world, BlockPos pos,
                                     BlockState iblockstate, LivingEntity entity, int numParticles) {
        BlockState particleState = this.getParticleState(world, pos);
        world.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, particleState), entity.getX(), entity.getY(), entity.getZ(),
                numParticles, 0, 0, 0, 0.15);
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public boolean addHitEffects(BlockState blockState, Level world, HitResult target, ParticleEngine pm) {
        if (!(target instanceof BlockHitResult) || !(world instanceof ClientLevel))
            return false;

        BlockHitResult hit = (BlockHitResult) target;
        BlockPos pos = hit.getBlockPos();
        BlockState state = this.getParticleState(world, pos);
        DiggingParticle fx;
        int i = pos.getX();
        int j = pos.getY();
        int k = pos.getZ();
        VoxelShape voxelShape = blockState.getShape(world, pos);
        AABB boundingBox = voxelShape.bounds();
        float f = 0.1F;
        double d0 = i + this.RANDOM.nextDouble() * (boundingBox.maxX - boundingBox.minX - (f * 2.0F)) + f + boundingBox.minX;
        double d1 = j + this.RANDOM.nextDouble() * (boundingBox.maxY - boundingBox.minY - (f * 2.0F)) + f + boundingBox.minY;
        double d2 = k + this.RANDOM.nextDouble() * (boundingBox.maxZ - boundingBox.minZ - (f * 2.0F)) + f + boundingBox.minZ;
        switch (hit.getDirection()) {
            case DOWN -> d1 = j + boundingBox.minY - f;
            case UP -> d1 = j + boundingBox.maxY + f;
            case NORTH -> d2 = k + boundingBox.minZ - f;
            case SOUTH -> d2 = k + boundingBox.maxZ + f;
            case WEST -> d0 = i + boundingBox.minX - f;
            case EAST -> d0 = i + boundingBox.maxX + f;
        }
        fx = new DiggingFX((ClientLevel) world, d0, d1, d2, 0, 0, 0, state);
        pm.add(fx.setBlockPos(pos).multiplyVelocity(0.2F).multiplyParticleScaleBy(0.6F));
        return true;
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public boolean addDestroyEffects(BlockState state, Level world, BlockPos pos, ParticleEngine pm) {
        if (!(world instanceof ClientLevel))
            return false;

        DiggingParticle fx;
        byte b0 = 4;
        for (int i = 0; i < b0; ++i) {
            for (int j = 0; j < b0; ++j) {
                for (int k = 0; k < b0; ++k) {
                    double d0 = pos.getX() + (i + 0.5D) / b0;
                    double d1 = pos.getY() + (j + 0.5D) / b0;
                    double d2 = pos.getZ() + (k + 0.5D) / b0;
                    fx = new DiggingFX((ClientLevel) world, d0, d1, d2,
                            d0 - pos.getX() - 0.5D, d1 - pos.getY() - 0.5D, d2 - pos.getZ() - 0.5D,
                            state);
                    pm.addEffect(fx.setBlockPos(pos));
                }
            }
        }
        return true;
    }

    public BlockState getParticleState(BlockAndTintGetter world, BlockPos pos) {
        return world.getBlockState(pos);
    }

    //@Override TODO: Replace with raytraceshape. We need mesh voxelization...
    //public RayTraceResult getRayTraceResult(BlockState state, Level world, BlockPos pos, Vector3d start, Vector3d end, RayTraceResult original) {
    //    boxHit = null;
    //    BlockRayTraceResult result = null;
    //    double nearestDistance = 0;
    //    List<AxisAlignedBB> list = getGlobalCollisionBoxes(world, pos, state, null);
    //    if (list != null) {
    //        int n = list.size();
    //        for (int i = 0; i < n; i++) {
    //            AxisAlignedBB box = list.get(i);
    //            BlockRayTraceResult mp = AxisAlignedBB.rayTrace(ImmutableList.of(box), start, end, pos);
    //            if (mp != null) {
    //                mp.subHit = i;
    //                double d = start.squareDistanceTo(mp.getHitVec());
    //                if (result == null || d < nearestDistance) {
    //                    result = mp;
    //                    nearestDistance = d;
    //                }
    //            }
    //        }
    //    }
    //    if (result != null) {
    //        //setBlockBounds(list.get(result.subHit));
    //        int i = result.subHit;
    //        boxHit = list.get(i).offset(-pos.getX(), -pos.getY(), -pos.getZ());
    //        result = new BlockRayTraceResult(result.getHitVec(), result.getFace(), pos, false);
    //        result.subHit = i;
    //    }
    //    return result;
    //}

    public static VoxelShape getCachedShape(ShapeContext context) {
        VoxelShape shape = SHAPE_CACHE.getUnchecked(context);
        if (shape.isEmpty()) {
            SHAPE_CACHE.invalidate(context);
            shape = Shapes.block();
        }
        return shape;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockAndTintGetter world, BlockPos pos, ISelectionContext context) {
        return getCachedShape(new ShapeContext((BlockStateArchitecture) state, world, pos));
    }

    //----------------------------- Bounds and collision boxes -----------------------------------

    @Nonnull
    protected VoxelShape getLocalBounds(BlockAndTintGetter world, BlockPos pos, BlockState state, Entity entity) {
        ModelSpec spec = this.getModelSpec(state);
        if (spec != null) {
            OBJSON model = ArchitectureMod.PROXY.getCachedOBJSON(spec.modelName);
            Trans3 t = this.localToGlobalTransformation(world, pos, state, Vector3.zero).translate(spec.origin);
            return t.t(model.getVoxelized());
        }
        return Shapes.empty();
    }

    @Nonnull
    protected VoxelShape getGlobalCollisionBoxes(BlockAndTintGetter world, BlockPos pos,
                                                 BlockState state, Entity entity) {
        Trans3 t = this.localToGlobalTransformation(world, pos, state);
        return this.getCollisionBoxes(world, pos, state, t, entity);
    }

    @Nonnull
    protected VoxelShape getLocalCollisionBoxes(BlockAndTintGetter world, BlockPos pos,
                                                BlockState state, Entity entity) {
        Trans3 t = this.localToGlobalTransformation(world, pos, state, Vector3.zero);
        return this.getCollisionBoxes(world, pos, state, t, entity);
    }

    @Nonnull
    protected VoxelShape getCollisionBoxes(BlockAndTintGetter world, BlockPos pos, BlockState state,
                                           Trans3 t, Entity entity) {
        ModelSpec spec = this.getModelSpec(state);
        if (spec != null) {
            OBJSON model = ArchitectureMod.PROXY.getCachedOBJSON(spec.modelName);
            return model.getShape(t.translate(spec.origin), Shapes.empty());
        }
        return Shapes.empty();
    }

    public float getBlockHardness(BlockState state, BlockAndTintGetter world, BlockPos pos, float hardness) {
        return hardness;
    }

    public boolean hasBlockEntity(BlockState state) {
        return false;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return null;
    }

    public interface IOrientationHandler {

        void defineProperties(BlockArchitecture block);

        BlockState onBlockPlaced(Block block, Level world, BlockPos pos, Direction side,
                                 double hitX, double hitY, double hitZ, BlockState baseState, LivingEntity placer);

        //Trans3 localToGlobalTransformation(IBlockReader world, BlockPos pos, IBlockState state);
        Trans3 localToGlobalTransformation(BlockAndTintGetter world, BlockPos pos, BlockState state, Vector3 origin);
    }

    public static class Orient1Way implements IOrientationHandler {

        public void defineProperties(BlockArchitecture block) {
        }

        public BlockState onBlockPlaced(Block block, Level world, BlockPos pos, Direction side,
                                        double hitX, double hitY, double hitZ, BlockState baseState, LivingEntity placer) {
            return baseState;
        }

        public Trans3 localToGlobalTransformation(BlockAndTintGetter world, BlockPos pos, BlockState state, Vector3 origin) {
            return new Trans3(origin);
        }

    }

    public class ShapeContext {
        private final BlockStateArchitecture state;
        private final BlockAndTintGetter world;
        private final BlockPos pos;

        public ShapeContext(BlockStateArchitecture state, BlockAndTintGetter world, BlockPos pos) {
            this.state = state;
            this.world = world;
            this.pos = pos;
        }

        @Override
        public int hashCode() {
            return this.state.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ShapeContext) {
                return ((ShapeContext) obj).state == this.state;
            }
            return false;
        }
    }

}
