package com.github.sirblobman.paid.brc.data.helper.listener;

import org.jetbrains.annotations.NotNull;

import com.github.sirblobman.api.folia.FoliaHelper;
import com.github.sirblobman.api.folia.details.RunnableTask;
import com.github.sirblobman.api.folia.scheduler.TaskScheduler;
import com.github.sirblobman.api.plugin.listener.PluginListener;
import com.github.sirblobman.paid.brc.data.helper.DataHelperPlugin;
import com.github.sirblobman.paid.brc.data.helper.manager.MySQLDataManager;

abstract class DataListener extends PluginListener<DataHelperPlugin> {
    DataListener(@NotNull DataHelperPlugin plugin) {
        super(plugin);
    }

    protected final @NotNull MySQLDataManager getDataManager() {
        DataHelperPlugin plugin = getPlugin();
        return plugin.getDataManager();
    }

    protected final void runAsync(@NotNull Runnable task) {
        DataHelperPlugin plugin = getPlugin();
        FoliaHelper foliaHelper = plugin.getFoliaHelper();
        TaskScheduler scheduler = foliaHelper.getScheduler();

        RunnableTask runnable = new RunnableTask(plugin, task);
        scheduler.scheduleAsyncTask(runnable);
    }
}
