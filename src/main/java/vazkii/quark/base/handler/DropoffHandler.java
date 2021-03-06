/**
 * This class was created by <Vazkii>. It's distributed as
 * part of the Quark Mod. Get the Source Code in github:
 * https://github.com/Vazkii/Quark
 *
 * Quark is Open Source and distributed under the
 * CC-BY-NC-SA 3.0 License: https://creativecommons.org/licenses/by-nc-sa/3.0/deed.en_GB
 *
 * File Created @ [28/03/2016, 16:47:58 (GMT)]
 */
package vazkii.quark.base.handler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;
import vazkii.arl.network.NetworkHandler;
import vazkii.quark.base.module.ModuleLoader;
import vazkii.quark.base.network.message.MessageDisableDropoffClient;
import vazkii.quark.management.feature.ChestButtons;
import vazkii.quark.management.feature.FavoriteItems;
import vazkii.quark.management.feature.StoreToChests;

public final class DropoffHandler {

	public static void dropoff(EntityPlayer player, boolean smart, boolean useContainer) {
		if(!ModuleLoader.isFeatureEnabled(useContainer ? ChestButtons.class : StoreToChests.class))
			return;

		if(!useContainer && !player.worldObj.getWorldInfo().getGameRulesInstance().getBoolean(StoreToChests.GAME_RULE)) {
			disableClientDropoff(player);
			return;
		}

		new Dropoff(player, smart, useContainer).execute();
	}

	public static void restock(EntityPlayer player) {
		if(!ModuleLoader.isFeatureEnabled(StoreToChests.class))
			return;

		new Restock(player).execute();
	}

	public static void disableClientDropoff(EntityPlayer player) {
		if(player instanceof EntityPlayerMP)
			NetworkHandler.INSTANCE.sendTo(new MessageDisableDropoffClient(), (EntityPlayerMP) player);
	}

	public static IItemHandler getInventory(EntityPlayer player, World world, BlockPos pos) {
		TileEntity te = world.getTileEntity(pos);

		if(te == null)
			return null;

		boolean accept = isValidChest(player, te);
		IItemHandler ret = te.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, null);

		if(accept && ret == null && te instanceof IInventory)
			ret = new InvWrapper((IInventory) te);

		return accept ? ret : null;
	}


	public static boolean isValidChest(EntityPlayer player, TileEntity te) {
		boolean accept = te instanceof IDropoffManager && ((IDropoffManager) te).acceptsDropoff();
		if(!accept) {
			String name = te.getClass().getSimpleName().toLowerCase();
			accept = (name.contains("chest") || te instanceof TileEntityChest) && !name.contains("void") && !name.contains("trash");
		}

		if(te instanceof IInventory)
			accept &= ((IInventory) te).isUseableByPlayer(player);

		return accept;
	}

	public static boolean isValidChest(EntityPlayer player, IInventory te) {
		boolean accept = te instanceof IDropoffManager && ((IDropoffManager) te).acceptsDropoff();
		if(!accept) {
			String name = te.getClass().getSimpleName().toLowerCase();
			accept = (name.contains("chest") || te instanceof TileEntityChest) && !name.contains("void") && !name.contains("trash");
		}

		accept &= te != null && te.isUseableByPlayer(player);

		return accept;
	}

	public static class Dropoff {

		final EntityPlayer player;
		final boolean smart;
		final boolean useContainer;

		List<Pair<IItemHandler, Double>> itemHandlers = new ArrayList();

		public Dropoff(EntityPlayer player, boolean smart, boolean useContainer) {
			this.player = player;
			this.useContainer = useContainer;
			this.smart = smart;
		}

		public void execute() {
			locateItemHandlers();

			if(itemHandlers.isEmpty())
				return;

			if(smart)
				smartDropoff();
			else roughDropoff();

			player.inventoryContainer.detectAndSendChanges();
			if(useContainer)
				player.openContainer.detectAndSendChanges();
		}

		public void smartDropoff() {
			dropoff((stack, handler) -> {
				int slots = handler.getSlots();
				for(int i = 0; i < slots; i++) {
					ItemStack stackAt = handler.getStackInSlot(i);
					if(stackAt == null)
						continue;

					boolean itemEqual = stack.getItem() == stackAt.getItem();
					boolean damageEqual = stack.getItemDamage() == stackAt.getItemDamage();
					boolean nbtEqual = ItemStack.areItemStackTagsEqual(stackAt, stack);

					if(itemEqual && damageEqual && nbtEqual)
						return true;

					if(!stack.getHasSubtypes() && stack.isItemStackDamageable() && stack.getMaxStackSize() == 1 && itemEqual && nbtEqual)
						return true;
				}

				return false;
			});
		}

		public void roughDropoff() {
			dropoff((stack, handler) -> true);
		}

		public void locateItemHandlers() {
			if(useContainer) {
				Container c = player.openContainer;
				for(Slot s : c.inventorySlots) {
					IInventory inv = s.inventory;
					if(inv != player.inventory) {
						itemHandlers.add(Pair.of(new InvWrapper(inv), 0.0));
						break;
					}
				}
			} else {
				BlockPos playerPos = player.getPosition();
				int range = 6;

				for(int i = -range; i < range * 2 + 1; i++)
					for(int j = -range; j < range * 2 + 1; j++)
						for(int k = -range; k < range * 2 + 1; k++) {
							BlockPos pos = playerPos.add(i, j, k);
							findHandler(pos);
						}

				Collections.sort(itemHandlers, (pair1, pair2) -> Double.compare(pair1.getRight(), pair2.getRight()));
			}
		}

		public void findHandler(BlockPos pos) {
			IItemHandler handler = getInventory(player, player.worldObj, pos);
			if(handler != null)
				itemHandlers.add(Pair.of(handler, player.getDistanceSq(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5)));
		}

		public void dropoff(DropoffPredicate pred) {
			InventoryPlayer inv = player.inventory;

			for(int i = InventoryPlayer.getHotbarSize(); i < inv.mainInventory.length; i++) {
				ItemStack stackAt = inv.getStackInSlot(i);

				if(stackAt != null && !FavoriteItems.isItemFavorited(stackAt)) {
					ItemStack ret = insert(stackAt, pred);
					if(!ItemStack.areItemsEqual(stackAt, ret))
						inv.setInventorySlotContents(i, ret);
				}
			}
		}

		public ItemStack insert(ItemStack stack, DropoffPredicate pred) {
			ItemStack ret = stack.copy();
			for(Pair<IItemHandler, Double> pair : itemHandlers) {
				IItemHandler handler = pair.getLeft();
				ret = insertInHandler(handler, stack, pred);
				if(ret == null)
					return null;
			}

			return ret;
		}

		public ItemStack insertInHandler(IItemHandler handler, final ItemStack stack, DropoffPredicate pred) {
			if(pred.apply(stack, handler)) {
				ItemStack retStack = ItemHandlerHelper.insertItemStacked(handler, stack, false);
				if(retStack != null)
					retStack = retStack.copy();

				if(retStack == null || retStack.stackSize == 0)
					return null;

				return retStack;
			}

			return stack;
		}

	}

	public static class Restock extends Dropoff {

		public Restock(EntityPlayer player) {
			super(player, true, true);
		}

		@Override
		public void dropoff(DropoffPredicate pred) {
			IItemHandler inv = itemHandlers.get(0).getLeft();
			IItemHandler playerInv = new PlayerInvWrapper(player.inventory);

			for(int i = inv.getSlots() - 1; i >= 0; i--) {
				ItemStack stackAt = inv.getStackInSlot(i);

				if(stackAt != null) {
					ItemStack copy = stackAt.copy();
					ItemStack ret = insertInHandler(playerInv, stackAt, pred);

					if(!ItemStack.areItemStacksEqual(copy, ret)) {
						inv.extractItem(i, 64, false);
						inv.insertItem(i, ret, false);
					}
				}
			}
		}
	}

	public static class PlayerInvWrapper extends InvWrapper {

		public PlayerInvWrapper(IInventory inv) {
			super(inv);
		}

		@Override
		public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
			if(stack != null)
				stack = stack.copy();

			return super.insertItem(slot, stack, simulate);
		}

		@Override
		public int getSlots() {
			return super.getSlots() - 5;
		}

	}

	public static interface DropoffPredicate {

		public boolean apply(ItemStack stack, IItemHandler handler);

	}

}
