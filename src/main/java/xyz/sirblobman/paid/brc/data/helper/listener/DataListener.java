package xyz.sirblobman.paid.brc.data.helper.listener;

import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;

import com.github.sirblobman.api.utility.Validate;
import xyz.sirblobman.paid.brc.data.helper.DataHelperPlugin;
import xyz.sirblobman.paid.brc.data.helper.manager.MySQLDataManager;

abstract class DataListener implements Listener {
    private final DataHelperPlugin plugin;
    DataListener(DataHelperPlugin plugin) {
        this.plugin = Validate.notNull(plugin, "plugin must not be null!");
    }

    public void register() {
        DataHelperPlugin plugin = getPlugin();
        PluginManager pluginManager = Bukkit.getPluginManager();
        pluginManager.registerEvents(this, plugin);
    }

    protected final DataHelperPlugin getPlugin() {
        return this.plugin;
    }

    protected final MySQLDataManager getDataManager() {
        DataHelperPlugin plugin = getPlugin();
        return plugin.getDataManager();
    }

    protected final void runAsync(Runnable task) {
        DataHelperPlugin plugin = getPlugin();
        BukkitScheduler scheduler = Bukkit.getScheduler();
        scheduler.runTaskAsynchronously(plugin, task);
    }
}
