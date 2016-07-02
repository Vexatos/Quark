/**
 * This class was created by <Vazkii>. It's distributed as
 * part of the Quark Mod. Get the Source Code in github:
 * https://github.com/Vazkii/Quark
 * 
 * Quark is Open Source and distributed under the
 * [ADD-LICENSE-HERE]
 * 
 * File Created @ [02/07/2016, 23:09:46 (GMT)]
 */
package vazkii.quark.world.entity;

import net.minecraft.client.resources.I18n;
import net.minecraft.entity.monster.EntitySkeleton;
import net.minecraft.entity.monster.SkeletonType;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.World;
import vazkii.quark.world.feature.DepthMobs;
import vazkii.quark.world.feature.PirateShips;

public class EntityPirate extends EntitySkeleton {

	public EntityPirate(World worldIn) {
		super(worldIn);
	}
	
	// TODO prevent hat from breaking

	@Override
	public String getName() {
        if(hasCustomName()) 
        	return getCustomNameTag();

		return I18n.format("entity.Quark.pirate.name");
	}
	
	@Override
	protected boolean canDespawn() {
		return false;
	}

	@Override
    protected void setEquipmentBasedOnDifficulty(DifficultyInstance difficulty) {
       setItemStackToSlot(EntityEquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
       setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(PirateShips.pirate_hat));
    }

	@Override
	public SkeletonType func_189771_df() {
		return SkeletonType.NORMAL;
	}
	
}
