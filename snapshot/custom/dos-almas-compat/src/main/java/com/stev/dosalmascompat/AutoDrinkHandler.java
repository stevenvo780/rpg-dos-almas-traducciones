package com.stev.dosalmascompat;

import dev.ghen.thirst.api.ThirstHelper;
import dev.ghen.thirst.foundation.common.capability.IThirst;
import dev.ghen.thirst.foundation.common.capability.ModCapabilities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.p3pp3rf1y.sophisticatedbackpacks.api.CapabilityBackpackWrapper;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.BackpackItem;
import net.p3pp3rf1y.sophisticatedbackpacks.backpack.wrapper.IBackpackWrapper;
import net.p3pp3rf1y.sophisticatedcore.inventory.ITrackedContentsItemHandler;
import net.p3pp3rf1y.sophisticatedcore.upgrades.feeding.FeedingUpgradeItem;
import net.p3pp3rf1y.sophisticatedcore.upgrades.feeding.FeedingUpgradeWrapper;
import net.p3pp3rf1y.sophisticatedcore.upgrades.feeding.HungerLevel;
import top.theillusivec4.curios.api.CuriosApi;

final class AutoDrinkHandler {
    private static final int MAX_THIRST = 20;
    private static final TagKey<Item> AUTO_DRINK_BLACKLIST = TagKey.create(
        Registries.ITEM,
        new ResourceLocation(DosAlmasCompat.MOD_ID, "auto_drink_blacklist")
    );

    private AutoDrinkHandler() {
    }

    static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (
            event.phase != TickEvent.Phase.END
                || event.player.level().isClientSide()
                || !(event.player instanceof ServerPlayer player)
                || !CompatConfig.AUTO_DRINK_ENABLED.get()
                || player.tickCount % CompatConfig.CHECK_INTERVAL_TICKS.get() != 0
        ) {
            return;
        }

        player.getCapability(ModCapabilities.PLAYER_THIRST).ifPresent(thirst -> {
            if (thirst.getThirst() >= MAX_THIRST) {
                return;
            }

            for (ItemStack backpack : carriedBackpacks(player)) {
                Optional<IBackpackWrapper> wrapper = backpack
                    .getCapability(CapabilityBackpackWrapper.getCapabilityInstance())
                    .resolve();
                if (wrapper.isPresent() && tryDrinkFromBackpack(player, thirst, wrapper.get())) {
                    return;
                }
            }
        });
    }

    private static List<ItemStack> carriedBackpacks(ServerPlayer player) {
        List<ItemStack> backpacks = new ArrayList<>();
        Set<ItemStack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Inventory inventory = player.getInventory();

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            addBackpack(inventory.getItem(slot), backpacks, seen);
        }

        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            IItemHandler equipped = curios.getEquippedCurios();
            for (int slot = 0; slot < equipped.getSlots(); slot++) {
                addBackpack(equipped.getStackInSlot(slot), backpacks, seen);
            }
        });

        return backpacks;
    }

    private static void addBackpack(
        ItemStack stack,
        List<ItemStack> backpacks,
        Set<ItemStack> seen
    ) {
        if (!stack.isEmpty() && stack.getItem() instanceof BackpackItem && seen.add(stack)) {
            backpacks.add(stack);
        }
    }

    private static boolean tryDrinkFromBackpack(
        ServerPlayer player,
        IThirst thirst,
        IBackpackWrapper backpack
    ) {
        ITrackedContentsItemHandler inventory = backpack.getInventoryForUpgradeProcessing();

        for (
            FeedingUpgradeWrapper feeding
                : backpack.getUpgradeHandler().getTypeWrappers(FeedingUpgradeItem.TYPE)
        ) {
            if (!feeding.isEnabled()) {
                continue;
            }

            Optional<DrinkCandidate> candidate = findBestDrink(
                inventory,
                feeding,
                thirst.getThirst()
            );
            if (
                candidate.isPresent()
                    && consumeDrink(player, inventory, candidate.get())
            ) {
                return true;
            }
        }

        return false;
    }

    private static Optional<DrinkCandidate> findBestDrink(
        ITrackedContentsItemHandler inventory,
        FeedingUpgradeWrapper feeding,
        int thirstLevel
    ) {
        List<DrinkCandidate> candidates = new ArrayList<>();
        int missingThirst = MAX_THIRST - thirstLevel;

        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (
                stack.isEmpty()
                    || stack.is(AUTO_DRINK_BLACKLIST)
                    || !feeding.getFilterLogic().matchesFilter(stack)
                    || !ThirstHelper.itemRestoresThirst(stack)
                    || !ThirstHelper.isDrink(stack)
            ) {
                continue;
            }

            int restoredThirst = Math.max(1, ThirstHelper.getThirst(stack));
            int purity = ThirstHelper.getPurity(stack);
            boolean unsafeWater = purity >= 0
                && purity < CompatConfig.MINIMUM_WATER_PURITY.get();
            boolean criticalFallback = CompatConfig.ALLOW_UNSAFE_WHEN_CRITICAL.get()
                && thirstLevel <= CompatConfig.CRITICAL_THIRST_LEVEL.get();

            if (
                unsafeWater && !criticalFallback
                    || !isThirstyEnough(
                        feeding.getFeedAtHungerLevel(),
                        missingThirst,
                        restoredThirst
                    )
            ) {
                continue;
            }

            candidates.add(
                new DrinkCandidate(
                    slot,
                    purity,
                    unsafeWater,
                    Math.max(0, restoredThirst - missingThirst)
                )
            );
        }

        return candidates.stream().min(
            Comparator
                .comparing(DrinkCandidate::unsafeWater)
                .thenComparing(
                    Comparator.comparingInt(DrinkCandidate::purityRank).reversed()
                )
                .thenComparingInt(DrinkCandidate::wastedThirst)
                .thenComparingInt(DrinkCandidate::slot)
        );
    }

    private static boolean isThirstyEnough(
        HungerLevel hungerLevel,
        int missingThirst,
        int restoredThirst
    ) {
        if (hungerLevel == HungerLevel.ANY) {
            return missingThirst > 0;
        }
        if (hungerLevel == HungerLevel.HALF) {
            return restoredThirst / 2 <= missingThirst;
        }
        return restoredThirst <= missingThirst;
    }

    private static boolean consumeDrink(
        ServerPlayer player,
        ITrackedContentsItemHandler backpackInventory,
        DrinkCandidate candidate
    ) {
        ItemStack storedStack = backpackInventory.getStackInSlot(candidate.slot());
        if (
            storedStack.isEmpty()
                || !ThirstHelper.itemRestoresThirst(storedStack)
                || !ThirstHelper.isDrink(storedStack)
        ) {
            return false;
        }

        Inventory playerInventory = player.getInventory();
        int selectedSlot = playerInventory.selected;
        ItemStack savedMainHand = playerInventory.items.get(selectedSlot);

        try {
            playerInventory.items.set(selectedSlot, storedStack);
            ItemStack singleDrink = storedStack.copyWithCount(1);

            InteractionResult useResult = singleDrink
                .use(player.level(), player, InteractionHand.MAIN_HAND)
                .getResult();
            if (!useResult.consumesAction()) {
                return false;
            }

            storedStack.shrink(1);
            backpackInventory.setStackInSlot(candidate.slot(), storedStack);

            ItemStack originalDrink = singleDrink.copy();
            ItemStack remainder = singleDrink
                .getItem()
                .finishUsingItem(singleDrink, player.level(), player);
            remainder = ForgeEventFactory.onItemUseFinish(
                player,
                originalDrink,
                0,
                remainder
            );

            if (!remainder.isEmpty()) {
                ItemStack notStored = backpackInventory.insertItem(remainder, false);
                if (!notStored.isEmpty()) {
                    ItemHandlerHelper.giveItemToPlayer(player, notStored);
                }
            }

            return true;
        } finally {
            playerInventory.items.set(selectedSlot, savedMainHand);
        }
    }

    private record DrinkCandidate(
        int slot,
        int purity,
        boolean unsafeWater,
        int wastedThirst
    ) {
        int purityRank() {
            return purity < 0 ? 4 : purity;
        }
    }
}
