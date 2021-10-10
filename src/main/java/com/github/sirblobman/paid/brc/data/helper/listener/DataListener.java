package com.github.sirblobman.paid.brc.data.helper.listener;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitScheduler;

import com.github.sirblobman.api.core.listener.PluginListener;
import com.github.sirblobman.paid.brc.data.helper.DataHelperPlugin;
import com.github.sirblobman.paid.brc.data.helper.manager.MySQLDataManager;

abstract class DataListener extends PluginListener<DataHelperPlugin> {
    DataListener(DataHelperPlugin plugin) {
        super(plugin);
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
