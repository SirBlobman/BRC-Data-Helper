package com.github.sirblobman.paid.brc.data.helper.listener;

import org.jetbrains.annotations.NotNull;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.ItemStack;

import com.github.sirblobman.paid.brc.data.helper.DataHelperPlugin;
import com.github.sirblobman.paid.brc.data.helper.manager.MySQLDataManager;

import net.brcdev.shopgui.event.ShopPostTransactionEvent;
import net.brcdev.shopgui.shop.Shop;
import net.brcdev.shopgui.shop.ShopManager.ShopAction;
import net.brcdev.shopgui.shop.ShopTransactionResult;
import net.brcdev.shopgui.shop.ShopTransactionResult.ShopTransactionResultType;
import net.brcdev.shopgui.shop.item.ShopItem;
import net.brcdev.shopgui.shop.item.ShopItemType;

public final class ListenerShopGUIPlus extends DataListener {
    public ListenerShopGUIPlus(@NotNull DataHelperPlugin plugin) {
        super(plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTransaction(ShopPostTransactionEvent e) {
        ShopTransactionResult result = e.getResult();
        ShopTransactionResultType resultType = result.getResult();
        if (resultType != ShopTransactionResultType.SUCCESS) {
            return;
        }

        ShopItem shopItem = result.getShopItem();
        ShopItemType shopItemType = shopItem.getType();
        if (shopItemType != ShopItemType.ITEM) {
            return;
        }

        Player player = result.getPlayer();
        ShopAction shopAction = result.getShopAction();
        String shopActionName = shopAction.name();

        int amount = result.getAmount();
        double price = result.getPrice();
        ItemStack item = shopItem.getItem();

        Shop shop = shopItem.getShop();
        String shopId = (shop == null ? "N/A" : shop.getId());
        long timestamp = System.currentTimeMillis();

        Runnable task = () -> {
            MySQLDataManager dataManager = getDataManager();
            dataManager.postAdminTransaction(player, shopId, shopActionName, amount, price, item, timestamp);
        };
        runAsync(task);
    }
}
