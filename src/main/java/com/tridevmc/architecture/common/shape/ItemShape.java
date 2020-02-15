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

package com.tridevmc.architecture.common.shape;

import com.tridevmc.architecture.common.ArchitectureContent;
import com.tridevmc.architecture.common.helpers.Utils;
import com.tridevmc.architecture.common.helpers.Vector3;
import com.tridevmc.architecture.common.tile.TileShape;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.*;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Random;

public class ItemShape extends ItemBlock {

    static Random rand = new Random();

    public ItemShape(Block block, Item.Properties builder) {
        super(block, builder);
    }

    @Override
    protected boolean placeBlock(BlockItemUseContext context, IBlockState newState) {
        EntityPlayer player = context.getPlayer();
        World world = context.getWorld();
        BlockPos pos = context.getPos();
        EnumFacing face = context.getFace();
        ItemStack stack = context.getItem();
        float hitX = context.getHitX();
        float hitY = context.getHitY();
        float hitZ = context.getHitZ();
        if (!world.setBlockState(pos, newState, 3))
            return false;
        Vec3i d = Vector3.getDirectionVec(face);
        Vector3 hit = new Vector3(hitX - d.getX() - 0.5, hitY - d.getY() - 0.5, hitZ - d.getZ() - 0.5);
        TileShape te = TileShape.get(world, pos);
        if (te != null) {
            te.readFromItemStack(stack);
            if (te.shape != null) {
                BlockPos npos = te.getPos().offset(face.getOpposite());
                IBlockState nstate = world.getBlockState(npos);
                TileEntity nte = world.getTileEntity(npos);
                te.shape.orientOnPlacement(player, te, npos, nstate, nte, face, hit);
            }
        }
        return true;
    }

    @Nullable
    @Override
    public NBTTagCompound getShareTag(ItemStack stack) {
        return stack.getTag();
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World worldIn, List<ITextComponent> lines, ITooltipFlag flagIn) {
        NBTTagCompound tag = stack.getTag();
        if (tag != null) {
            int id = tag.getInt("Shape");
            Shape shape = Shape.forId(id);
            if (shape != null)
                lines.set(0, new TextComponentString(shape.title));
            else
                lines.set(0, new TextComponentString(lines.get(0).getFormattedText() + " (" + id + ")"));
            Block baseBlock = Block.getStateById(tag.getInt("Block")).getBlock();
            lines.add(new TextComponentString(Utils.displayNameOfBlock(baseBlock)));
        }
    }

    @Override
    public void fillItemGroup(ItemGroup group, NonNullList<ItemStack> items) {
        if (group == ArchitectureContent.SHAPE_TAB) {
            for (Shape shape : Shape.values()) {
                if (shape.isCladding())
                    continue;

                ItemStack defaultStack = new ItemStack(this, 1);
                NBTTagCompound tag = new NBTTagCompound();
                tag.putInt("Shape", shape.id);
                tag.putInt("Block", Block.getStateId(Blocks.OAK_PLANKS.getDefaultState()));
                defaultStack.setTag(tag);
                items.add(defaultStack);
            }
        }

        super.fillItemGroup(group, items);
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack defaultStack = new ItemStack(this, 1);
        NBTTagCompound tag = new NBTTagCompound();
        tag.putInt("Shape", Shape.ROOF_TILE.id);
        tag.putInt("Block", Block.getStateId(Blocks.OAK_PLANKS.getDefaultState()));
        defaultStack.setTag(tag);

        return defaultStack;
    }

    @Override
    public ITextComponent getDisplayName(ItemStack stack) {
        NBTTagCompound tag = stack.getTag();
        if (tag == null)
            return super.getDisplayName(stack);

        int id = tag.getInt("Shape");
        Shape shape = Shape.forId(id);
        IBlockState state = Block.getStateById(tag.getInt("Block"));
        return new TextComponentString(shape.title + ": " + Utils.displayNameOnlyOfBlock(state.getBlock()));
    }
}