package xyz.sirblobman.paid.brc.data.helper;

import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;

import com.github.sirblobman.api.configuration.ConfigurationManager;
import com.github.sirblobman.api.plugin.ConfigurablePlugin;
import xyz.sirblobman.paid.brc.data.helper.listener.ListenerPlayerShopGUIPlus;
import xyz.sirblobman.paid.brc.data.helper.listener.ListenerShopGUIPlus;
import xyz.sirblobman.paid.brc.data.helper.manager.MySQLDataManager;

public final class DataHelperPlugin extends ConfigurablePlugin {
    private final MySQLDataManager dataManager;
    private boolean enabledSuccessfully;

    public DataHelperPlugin() {
        this.dataManager = new MySQLDataManager(this);
        this.enabledSuccessfully = false;
    }

    @Override
    public void onLoad() {
        ConfigurationManager configurationManager = getConfigurationManager();
        configurationManager.saveDefault("config.yml");
    }

    @Override
    public void onEnable() {
        MySQLDataManager dataManager = getDataManager();
        if(!dataManager.connectToDatabase()) {
            setEnabled(false);
            return;
        }

        showVersionInfo("PlayerShopGUIPlus");
        showVersionInfo("ShopGUIPlus");
        showVersionInfo("SirBlobmanCore");
        dataManager.register();

        new ListenerPlayerShopGUIPlus(this).register();
        new ListenerShopGUIPlus(this).register();
        this.enabledSuccessfully = true;
    }

    @Override
    public void onDisable() {
        if(!this.enabledSuccessfully) {
            return;
        }

        if(!this.dataManager.isCancelled()) {
            this.dataManager.cancel();
        }

        this.enabledSuccessfully = false;
    }

    public MySQLDataManager getDataManager() {
        return this.dataManager;
    }

    private void showVersionInfo(String pluginName) {
        PluginManager pluginManager = Bukkit.getPluginManager();
        Plugin plugin = pluginManager.getPlugin(pluginName);
        if(plugin == null) return;

        PluginDescriptionFile pluginDescription = plugin.getDescription();
        String pluginFullName = pluginDescription.getFullName();

        Logger logger = getLogger();
        logger.info("Found a dependency: " + pluginFullName + "");
    }
}
