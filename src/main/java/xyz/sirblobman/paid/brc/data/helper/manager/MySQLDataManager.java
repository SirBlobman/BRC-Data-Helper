package xyz.sirblobman.paid.brc.data.helper.manager;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import com.github.sirblobman.api.configuration.ConfigurationManager;
import com.github.sirblobman.api.core.CorePlugin;
import com.github.sirblobman.api.utility.ItemUtility;
import com.github.sirblobman.api.utility.Validate;
import xyz.sirblobman.paid.brc.data.helper.DataHelperPlugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.brcdev.playershopgui.shop.ShopItem;
import net.brcdev.playershopgui.shop.ShopItem.ShopItemState;
import org.jetbrains.annotations.Nullable;
import org.mariadb.jdbc.MariaDbPoolDataSource;

public final class MySQLDataManager extends BukkitRunnable {
    private final DataHelperPlugin plugin;
    private final MariaDbPoolDataSource dataSource;
    public MySQLDataManager(DataHelperPlugin plugin) {
        this.plugin = Validate.notNull(plugin, "plugin must not be null!");
        this.dataSource = new MariaDbPoolDataSource();
    }

    public synchronized boolean connectToDatabase() {
        ConfigurationManager configurationManager = this.plugin.getConfigurationManager();
        YamlConfiguration configuration = configurationManager.get("config.yml");

        String hostName = configuration.getString("database.host");
        int port = configuration.getInt("database.port");
        String databaseName = configuration.getString("database.database");
        String userName = configuration.getString("database.username");
        String userPass = configuration.getString("database.password");

        Logger logger = this.plugin.getLogger();
        try {
            this.dataSource.setServerName(hostName);
            this.dataSource.setPortNumber(port);
            this.dataSource.setDatabaseName(databaseName);
            this.dataSource.setUser(userName);
            this.dataSource.setPassword(userPass);
            this.dataSource.initialize();

            Connection connection = getConnection();
            DatabaseMetaData connectionMeta = connection.getMetaData();
            String driverName = connectionMeta.getDriverName();
            String driverVersion = connectionMeta.getDriverVersion();
            String driverFullName = String.format(Locale.US, "%s v%s", driverName, driverVersion);
            logger.info("Successfully connected to MariaDB database with driver " + driverFullName + ".");

            logger.info("Checking database tables...");
            checkDatabaseTables(connection);
            logger.info("Done.");

            return true;
        } catch (SQLException ex) {
            logger.log(Level.WARNING,"Failed to setup the MySQL database connection because an error occurred:", ex);
            return false;
        }
    }

    public void register() {
        ConfigurationManager configurationManager = this.plugin.getConfigurationManager();
        YamlConfiguration configuration = configurationManager.get("config.yml");
        long period = configuration.getLong("data-sync-period");
        runTaskTimerAsynchronously(this.plugin, 1L, period);
    }

    @Override
    public void run() {
        Logger logger = this.plugin.getLogger();
        logger.info("Data synchronization triggered...");

        try (Connection connection = getConnection()) {
            convertPSGPTable(connection);
            logger.info("Data synchronization completed.");
        } catch(SQLException ex) {
            logger.log(Level.WARNING, "An error occurred while syncing data to MySQL:", ex);
            logger.warning("Data synchronization failed.");
        }
    }

    public synchronized void postPlayerTransaction(Player player, String shopName, UUID shopOwner, double price, int amount, ItemStack item, long timestamp) {
        ConfigurationManager configurationManager = this.plugin.getConfigurationManager();
        YamlConfiguration configuration = configurationManager.get("config.yml");
        String tableName = configuration.getString("tables.psgp-purchase-history");

        JsonElement itemElement = toJSON(item);
        if(itemElement == null) return;

        String buyerId = player.getUniqueId().toString();
        String shopOwnerId = shopOwner.toString();
        String itemJson = itemElement.toString();

        try (Connection connection = getConnection()) {
            String sqlCode = String.format(Locale.US, "INSERT INTO `%s` (`buyer_id`, `shop_name`, " +
                    "`shop_owner_id`, `price`, `amount`, `item_json`, `timestamp`) VALUES (?, ?, ?, ?, ?, ?, ?);", tableName);
            PreparedStatement preparedStatement = connection.prepareStatement(sqlCode);

            preparedStatement.setString(1, buyerId);
            preparedStatement.setString(2, shopName);
            preparedStatement.setString(3, shopOwnerId);
            preparedStatement.setDouble(4, price);
            preparedStatement.setInt(5, amount);
            preparedStatement.setString(6, itemJson);
            preparedStatement.setTimestamp(7, new Timestamp(timestamp));

            preparedStatement.executeUpdate();
            preparedStatement.close();
        } catch(SQLException ex) {
            Logger logger = this.plugin.getLogger();
            logger.log(Level.WARNING, "An error occurred while posting a transaction to MySQL:", ex);
        }
    }

    public synchronized void postPlayerShopCreation(Player player, String shopName, ItemStack item, int amount, double price, long timestamp) {
        ConfigurationManager configurationManager = this.plugin.getConfigurationManager();
        YamlConfiguration configuration = configurationManager.get("config.yml");
        String tableName = configuration.getString("tables.psgp-creation");

        JsonElement itemElement = toJSON(item);
        if(itemElement == null) return;

        String playerId = player.getUniqueId().toString();
        String itemJson = itemElement.toString();

        try (Connection connection = getConnection()) {
            String sqlCode = String.format(Locale.US, "INSERT INTO `%s` (`player_id`, `shop_name`, " +
                    "`price`, `amount`, `item_json`, `timestamp`) VALUES (?, ?, ?, ?, ?, ?);", tableName);
            PreparedStatement preparedStatement = connection.prepareStatement(sqlCode);

            preparedStatement.setString(1, playerId);
            preparedStatement.setString(2, shopName);
            preparedStatement.setDouble(3, price);
            preparedStatement.setInt(4, amount);
            preparedStatement.setString(5, itemJson);
            preparedStatement.setTimestamp(6, new Timestamp(timestamp));

            preparedStatement.executeUpdate();
            preparedStatement.close();
        } catch(SQLException ex) {
            Logger logger = this.plugin.getLogger();
            logger.log(Level.WARNING, "An error occurred while posting an item creation to MySQL:", ex);
        }
    }

    public void postAdminTransaction(Player player, String shopAction, int amount, double price, ItemStack item, long timestamp) {
        ConfigurationManager configurationManager = this.plugin.getConfigurationManager();
        YamlConfiguration configuration = configurationManager.get("config.yml");
        String tableName = configuration.getString("tables.sgp-transaction-history");

        JsonElement itemElement = toJSON(item);
        if(itemElement == null) return;

        String playerId = player.getUniqueId().toString();
        String itemJson = itemElement.toString();

        try (Connection connection = getConnection()) {
            String sqlCode = String.format(Locale.US, "INSERT INTO `%s` (`buyer_id`, `timestamp`, " +
                    "`transaction_type`, `price`, `amount`, `item_json`) VALUES (?, ?, ?, ?, ?, ?);", tableName);
            PreparedStatement preparedStatement = connection.prepareStatement(sqlCode);

            preparedStatement.setString(1, playerId);
            preparedStatement.setTimestamp(2, new Timestamp(timestamp));
            preparedStatement.setString(3, shopAction);
            preparedStatement.setDouble(4, price);
            preparedStatement.setInt(5, amount);
            preparedStatement.setString(6, itemJson);

            preparedStatement.executeUpdate();
            preparedStatement.close();
        } catch(SQLException ex) {
            Logger logger = this.plugin.getLogger();
            logger.log(Level.WARNING, "An error occurred while posting an item creation to MySQL:", ex);
        }
    }

    private synchronized Connection getConnection() throws SQLException {
        return this.dataSource.getConnection();
    }

    private synchronized void checkDatabaseTables(Connection connection) throws SQLException {
        ConfigurationManager configurationManager = this.plugin.getConfigurationManager();
        YamlConfiguration configuration = configurationManager.get("config.yml");
        String originalTableName = configuration.getString("tables.psgp-original");

        DatabaseMetaData connectionMeta = connection.getMetaData();
        ResultSet results = connectionMeta.getTables(null, null, originalTableName, null);
        if(!results.next()) throw new SQLException("Original PSG+ table `" + originalTableName + "` does not exist!");
        results.close();

        createConvertedTable(connection);
        createPurchaseHistoryTable(connection);
        createPlayerShopCreationHistoryTable(connection);
        createAdminShopTransactionTable(connection);
    }

    private synchronized void execute(Connection connection, String sql) throws SQLException {
        Statement statement = connection.createStatement();
        statement.execute(sql);
        statement.close();
    }

    private synchronized void createTable(Connection connection, String tableNamePath, String tableColumns) throws SQLException {
        ConfigurationManager configurationManager = this.plugin.getConfigurationManager();
        YamlConfiguration configuration = configurationManager.get("config.yml");
        String tableName = configuration.getString(tableNamePath);

        String sqlCode = ("CREATE TABLE IF NOT EXISTS `%s` (%s);");
        String createTableCode = String.format(Locale.US, sqlCode, tableName, tableColumns);
        execute(connection, createTableCode);
    }

    private synchronized void createConvertedTable(Connection connection) throws SQLException {
        List<String> columnList = List.of(
                "`id` INTEGER PRIMARY KEY",
                "`player_id` VARCHAR(36) NOT NULL",
                "`shop_name` VARCHAR(255)",
                "`shop_items` JSON",
                "`shop_items_count` INTEGER DEFAULT 0"
        );

        String tableColumns = String.join(", ", columnList);
        createTable(connection, "tables.psgp-converted", tableColumns);
    }

    private synchronized void createPurchaseHistoryTable(Connection connection) throws SQLException {
        List<String> columnList = List.of(
                "`id` INTEGER PRIMARY KEY AUTO_INCREMENT",
                "`buyer_id` VARCHAR(36) NOT NULL",
                "`shop_name` VARCHAR(255)",
                "`shop_owner_id` VARCHAR(255)",
                "`timestamp` TIMESTAMP DEFAULT CURRENT_TIMESTAMP",
                "`price` DOUBLE NOT NULL",
                "`amount` INTEGER NOT NULL",
                "`item_json` JSON NOT NULL"
        );

        String tableColumns = String.join(", ", columnList);
        createTable(connection, "tables.psgp-purchase-history", tableColumns);
    }

    private synchronized void createPlayerShopCreationHistoryTable(Connection connection) throws SQLException {
        List<String> columnList = List.of(
                "`id` INTEGER PRIMARY KEY AUTO_INCREMENT",
                "`player_id` VARCHAR(36) NOT NULL",
                "`timestamp` TIMESTAMP DEFAULT CURRENT_TIMESTAMP",
                "`shop_name` VARCHAR(255) NOT NULL",
                "`price` DOUBLE NOT NULL",
                "`amount` INTEGER NOT NULL",
                "`item_json` JSON NOT NULL"
        );

        String tableColumns = String.join(", ", columnList);
        createTable(connection, "tables.psgp-creation", tableColumns);
    }

    private synchronized void createAdminShopTransactionTable(Connection connection) throws SQLException {
        List<String> columnList = List.of(
                "`id` INTEGER PRIMARY KEY AUTO_INCREMENT",
                "`buyer_id` VARCHAR(36) NOT NULL",
                "`timestamp` TIMESTAMP DEFAULT CURRENT_TIMESTAMP",
                "`transaction_type` VARCHAR(8) NOT NULL",
                "`price` DOUBLE NOT NULL",
                "`amount` INTEGER NOT NULL",
                "`item_json` JSON NOT NULL"
        );

        String tableColumns = String.join(", ", columnList);
        createTable(connection, "tables.sgp-transaction-history", tableColumns);
    }

    private synchronized void convertPSGPTable(Connection connection) throws SQLException {
        ConfigurationManager configurationManager = this.plugin.getConfigurationManager();
        YamlConfiguration configuration = configurationManager.get("config.yml");
        String originalTableName = configuration.getString("tables.psgp-original");
        String convertedTableName = configuration.getString("tables.psgp-converted");

        String deleteCode = String.format(Locale.US, "DELETE FROM `%s` WHERE `id` NOT IN (SELECT `id` FROM `%s`);", convertedTableName, originalTableName);
        execute(connection, deleteCode);

        String selectAllCode = ("SELECT * FROM `%s` WHERE `shopItems` != '[]';");
        String selectAll = String.format(Locale.US, selectAllCode, originalTableName, convertedTableName);
        Statement selectAllStatement = connection.createStatement();
        ResultSet selectAllResults = selectAllStatement.executeQuery(selectAll);

        String insert = String.format(Locale.US, "INSERT INTO `%s` (`id`,`player_id`,`shop_name`,`shop_items`, " +
                "`shop_items_count`) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE `shop_name`=VALUES(`shop_name`), " +
                "`shop_items`=VALUES(`shop_items`), `shop_items_count`=VALUES(`shop_items_count`);", convertedTableName);
        PreparedStatement insertPrepared = connection.prepareStatement(insert);

        while(selectAllResults.next()) {
            int id = selectAllResults.getInt("id");
            String shopName = selectAllResults.getString("name");
            String ownerUuid = selectAllResults.getString("ownerUuid");
            String shopItemsJson = selectAllResults.getString("shopItems");

            UUID playerId = getUUID(ownerUuid);
            if(playerId == null) continue;

            net.brcdev.playershopgui.shop.ShopItem[] shopItemArray = net.brcdev.playershopgui.util.GsonUtils.getGson().fromJson(shopItemsJson, net.brcdev.playershopgui.shop.ShopItem[].class);
            if(shopItemArray == null || shopItemArray.length == 0) continue;

            JsonArray jsonArray = new JsonArray();
            for(ShopItem shopItem : shopItemArray) {
                ShopItemState state = shopItem.getState();
                if(state == ShopItemState.CANCELLED || state == ShopItemState.EXPIRED) continue;

                long endTimestamp = shopItem.getEndTimestamp();
                if(endTimestamp <= System.currentTimeMillis()) continue;

                ItemStack item = shopItem.getItemStack();
                JsonElement jsonElement = toJSON(item);
                if(jsonElement == null) continue;

                double price = shopItem.getPrice();
                JsonObject jsonObject = jsonElement.getAsJsonObject();
                jsonObject.addProperty("price", price);
                jsonObject.addProperty("end-timestamp", endTimestamp);
                jsonArray.add(jsonObject);
            }
            if(jsonArray.size() == 0) continue;

            insertPrepared.setInt(1, id);
            insertPrepared.setString(2, playerId.toString());
            insertPrepared.setString(3, shopName);
            insertPrepared.setString(4, jsonArray.toString());
            insertPrepared.setInt(5, jsonArray.size());
            insertPrepared.addBatch();
        }

        insertPrepared.executeLargeBatch();
        insertPrepared.close();

        selectAllResults.close();
        selectAllStatement.close();
    }

    @Nullable
    private UUID getUUID(String string) {
        try {
            return UUID.fromString(string);
        } catch(IllegalArgumentException ex) {
            return null;
        }
    }

    @Nullable
    private JsonElement toJSON(ItemStack item) {
        if(ItemUtility.isAir(item)) return null;

        String json = JavaPlugin.getPlugin(CorePlugin.class).getMultiVersionHandler().getItemHandler().toNBT(item);
        JsonParser jsonParser = new JsonParser();
        return jsonParser.parse(json);
    }
}