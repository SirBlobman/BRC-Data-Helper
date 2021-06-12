package xyz.sirblobman.paid.brc.data.helper.listener;

import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.inventory.ItemStack;

import xyz.sirblobman.paid.brc.data.helper.DataHelperPlugin;
import xyz.sirblobman.paid.brc.data.helper.manager.MySQLDataManager;

import net.brcdev.playershopgui.event.CreateItemLotEvent;
import net.brcdev.playershopgui.event.PurchaseItemLotEvent;
import net.brcdev.playershopgui.shop.Shop;
import net.brcdev.playershopgui.shop.ShopItem;

public final class ListenerPlayerShopGUIPlus extends DataListener {
    public ListenerPlayerShopGUIPlus(DataHelperPlugin plugin) {
        super(plugin);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onPurchase(PurchaseItemLotEvent e) {
        Player player = e.getBuyer();
        ShopItem shopItem = e.getShopItem();
        Shop shop = e.getShop();

        int amount = e.getQuantity();
        ItemStack item = shopItem.getItemStack();
        double price = shopItem.getPrice(amount);

        String shopName = shop.getName();
        UUID shopOwner = shop.getOwnerUuid();

        long timestamp = System.currentTimeMillis();
        Runnable task = () -> {
            MySQLDataManager dataManager = getDataManager();
            dataManager.postPlayerTransaction(player, shopName, shopOwner, price, amount, item, timestamp);
        };
        runAsync(task);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onCreate(CreateItemLotEvent e) {
        Player player = e.getPlayer();
        Shop shop = e.getShop();
        String shopName = shop.getName();

        ItemStack item = e.getItemStack();
        int amount = e.getQuantity();
        double price = e.getPrice();

        long timestamp = System.currentTimeMillis();
        Runnable task = () -> {
            MySQLDataManager dataManager = getDataManager();
            dataManager.postPlayerShopCreation(player, shopName, item, amount, price, timestamp);
        };
        runAsync(task);
    }
}
