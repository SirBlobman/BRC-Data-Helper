package com.github.sirblobman.paid.brc.data.helper.manager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import com.github.sirblobman.api.configuration.ConfigurationManager;
import com.github.sirblobman.api.core.CorePlugin;
import com.github.sirblobman.api.nms.ItemHandler;
import com.github.sirblobman.api.nms.MultiVersionHandler;
import com.github.sirblobman.api.utility.ItemUtility;
import com.github.sirblobman.paid.brc.data.helper.DataHelperPlugin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mysql.cj.jdbc.MysqlConnectionPoolDataSource;
import com.mysql.cj.jdbc.MysqlDataSource;
import net.brcdev.playershopgui.shop.ShopItem;
import net.brcdev.playershopgui.shop.ShopItem.ShopItemState;

public final class MySQLDataManager extends TimerTask {
    private final DataHelperPlugin plugin;
    private final MysqlDataSource dataSource;
    private Timer timer;

    public MySQLDataManager(@NotNull DataHelperPlugin plugin) {
        this.plugin = plugin;
        this.dataSource = new MysqlConnectionPoolDataSource();
        this.timer = null;
    }

    @Override
    public void run() {
        printDebug("Data synchronization triggered...");

        try (Connection connection = getConnection()) {
            convertPSGPTable(connection);
            printDebug("Data synchronization completed.");
        } catch (SQLException ex) {
            Logger logger = this.plugin.getLogger();
            logger.log(Level.WARNING, "An error occurred while syncing data to MySQL:", ex);
            logger.warning("Data synchronization failed.");
        }
    }

    @Override
    public boolean cancel() {
        boolean cancel = super.cancel();

        if (this.timer != null) {
            this.timer.cancel();
            this.timer = null;
        }

        return cancel;
    }

    public boolean isCancelled() {
        return (this.timer == null);
    }

    public void register() {
        if (this.timer != null) {
            cancel();
        }

        ConfigurationManager configurationManager = this.plugin.getConfigurationManager();
        YamlConfiguration configuration = configurationManager.get("config.yml");
        long periodTicks = configuration.getLong("data-sync-period");
        long periodMillis = (periodTicks * 50L);

        this.timer = new Timer("BRC Data Helper Synchronization");
        this.timer.scheduleAtFixedRate(this, 50L, periodMillis);
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
            this.dataSource.setLoginTimeout(5);

            Connection connection = getConnection();
            DatabaseMetaData connectionMeta = connection.getMetaData();
            String driverName = connectionMeta.getDriverName();
            String driverVersion = connectionMeta.getDriverVersion();
            String driverFullName = String.format(Locale.US, "%s v%s", driverName, driverVersion);
            printDebug("Successfully connected to MySQL database with driver " + driverFullName + ".");

            printDebug("Checking database tables...");
            checkDatabaseTables(connection);
            printDebug("Done.");

            connection.close();
            return true;
        } catch (SQLException ex) {
            logger.log(Level.WARNING, "Failed to setup the MySQL database connection because an error occurred:", ex);
            return false;
        }
    }

    public synchronized void postPlayerTransaction(@NotNull Player player, @NotNull String shopName,
                                                   @NotNull UUID shopOwner, double price, int amount,
                                                   @NotNull ItemStack item, long timestamp) {
        ConfigurationManager configurationManager = this.plugin.getConfigurationManager();
        YamlConfiguration configuration = configurationManager.get("config.yml");
        String tableName = configuration.getString("tables.psgp-purchase-history");

        JsonElement itemElement = toJSON(item);
        if (itemElement == null) {
            return;
        }

        String buyerId = player.getUniqueId().toString();
        String shopOwnerId = shopOwner.toString();
        String itemJson = itemElement.toString();

        try (Connection connection = getConnection()) {
            String insertCode = getCommandFromSQL("insert_into_psgp_purchase_history", tableName);
            PreparedStatement preparedStatement = connection.prepareStatement(insertCode);

            preparedStatement.setString(1, buyerId);
            preparedStatement.setString(2, shopName);
            preparedStatement.setString(3, shopOwnerId);
            preparedStatement.setDouble(4, price);
            preparedStatement.setInt(5, amount);
            preparedStatement.setString(6, itemJson);
            preparedStatement.setTimestamp(7, new Timestamp(timestamp));

            preparedStatement.executeUpdate();
            preparedStatement.close();
        } catch (SQLException ex) {
            Logger logger = this.plugin.getLogger();
            logger.log(Level.WARNING, "An error occurred while posting a transaction to MySQL:", ex);
        }
    }

    public synchronized void postPlayerShopCreation(@NotNull Player player, @NotNull String shopName,
                                                    @NotNull ItemStack item, int amount, double price, long timestamp) {
        ConfigurationManager configurationManager = this.plugin.getConfigurationManager();
        YamlConfiguration configuration = configurationManager.get("config.yml");
        String tableName = configuration.getString("tables.psgp-creation");

        JsonElement itemElement = toJSON(item);
        if (itemElement == null) {
            return;
        }

        String playerId = player.getUniqueId().toString();
        String itemJson = itemElement.toString();

        try (Connection connection = getConnection()) {
            String insertCode = getCommandFromSQL("insert_into_psgp_creation", tableName);
            PreparedStatement preparedStatement = connection.prepareStatement(insertCode);

            preparedStatement.setString(1, playerId);
            preparedStatement.setString(2, shopName);
            preparedStatement.setDouble(3, price);
            preparedStatement.setInt(4, amount);
            preparedStatement.setString(5, itemJson);
            preparedStatement.setTimestamp(6, new Timestamp(timestamp));

            preparedStatement.executeUpdate();
            preparedStatement.close();
        } catch (SQLException ex) {
            Logger logger = this.plugin.getLogger();
            logger.log(Level.WARNING, "An error occurred while posting an item creation to MySQL:", ex);
        }
    }

    public void postAdminTransaction(@NotNull Player player, @NotNull String shopId, @NotNull String shopAction,
                                     int amount, double price, @NotNull ItemStack item, long timestamp) {
        ConfigurationManager configurationManager = this.plugin.getConfigurationManager();
        YamlConfiguration configuration = configurationManager.get("config.yml");
        String tableName = configuration.getString("tables.sgp-transaction-history");

        JsonElement itemElement = toJSON(item);
        if (itemElement == null) {
            return;
        }

        String playerId = player.getUniqueId().toString();
        String itemJson = itemElement.toString();

        try (Connection connection = getConnection()) {
            String insertCode = getCommandFromSQL("insert_into_sgp_transaction", tableName);
            PreparedStatement preparedStatement = connection.prepareStatement(insertCode);

            preparedStatement.setString(1, playerId);
            preparedStatement.setTimestamp(2, new Timestamp(timestamp));
            preparedStatement.setString(3, shopAction);
            preparedStatement.setDouble(4, price);
            preparedStatement.setInt(5, amount);
            preparedStatement.setString(6, itemJson);
            preparedStatement.setString(7, shopId);

            preparedStatement.executeUpdate();
            preparedStatement.close();
        } catch (SQLException ex) {
            Logger logger = this.plugin.getLogger();
            logger.log(Level.WARNING, "An error occurred while posting an item creation to MySQL:", ex);
        }
    }

    private synchronized @NotNull Connection getConnection() throws SQLException {
        return this.dataSource.getConnection();
    }

    private synchronized void checkDatabaseTables(@NotNull Connection connection) throws SQLException {
        ConfigurationManager configurationManager = this.plugin.getConfigurationManager();
        YamlConfiguration configuration = configurationManager.get("config.yml");
        String originalTableName = configuration.getString("tables.psgp-original");

        DatabaseMetaData connectionMeta = connection.getMetaData();
        ResultSet results = connectionMeta.getTables(null, null, originalTableName, null);
        if (!results.next()) {
            throw new SQLException("Original PSG+ table `" + originalTableName + "` does not exist!");
        }

        results.close();

        // Create any missing tables
        createConvertedTable(connection);
        createPurchaseHistoryTable(connection);
        createPlayerShopCreationHistoryTable(connection);
        createAdminShopTransactionTable(connection);

        // Fix missing column from admin shop table when it's missing.
        checkAdminShopTransactionTable(connection);
    }

    private synchronized void checkAdminShopTransactionTable(@NotNull Connection connection) throws SQLException {
        ConfigurationManager configurationManager = this.plugin.getConfigurationManager();
        YamlConfiguration configuration = configurationManager.get("config.yml");
        String databaseName = configuration.getString("database.database");
        String tableName = configuration.getString("tables.sgp-transaction-history");

        String sqlCommand = getCommandFromSQL("select_shop_column_in_sgp_transaction");
        PreparedStatement statement = connection.prepareStatement(sqlCommand);
        statement.setString(1, databaseName);
        statement.setString(2, tableName);

        ResultSet results = statement.executeQuery();
        if (!results.next() || results.getInt("COUNT(*)") == 0) {
            String createCommand = getCommandFromSQL("create_shop_id_column_in_sgp_transaction",
                    tableName);
            Statement createStatement = connection.createStatement();
            createStatement.execute(createCommand);
            createStatement.close();
        }

        results.close();
        statement.close();
    }

    private synchronized void execute(@NotNull Connection connection, @NotNull String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private synchronized void createTable(@NotNull Connection connection, @NotNull String tableNamePath,
                                          @NotNull String tableColumns) throws SQLException {
        ConfigurationManager configurationManager = this.plugin.getConfigurationManager();
        YamlConfiguration configuration = configurationManager.get("config.yml");
        String tableName = configuration.getString(tableNamePath);

        String sqlCode = ("CREATE TABLE IF NOT EXISTS `%s` (%s);");
        String createTableCode = String.format(Locale.US, sqlCode, tableName, tableColumns);
        execute(connection, createTableCode);
    }

    private synchronized void createConvertedTable(@NotNull Connection connection) throws SQLException {
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

    private synchronized void createPurchaseHistoryTable(@NotNull Connection connection) throws SQLException {
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

    private synchronized void createPlayerShopCreationHistoryTable(@NotNull Connection connection) throws SQLException {
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

    private synchronized void createAdminShopTransactionTable(@NotNull Connection connection) throws SQLException {
        List<String> columnList = List.of(
                "`id` INTEGER PRIMARY KEY AUTO_INCREMENT",
                "`buyer_id` VARCHAR(36) NOT NULL",
                "`timestamp` TIMESTAMP DEFAULT CURRENT_TIMESTAMP",
                "`transaction_type` VARCHAR(8) NOT NULL",
                "`price` DOUBLE NOT NULL",
                "`amount` INTEGER NOT NULL",
                "`item_json` JSON NOT NULL",
                "`shop-id` VARCHAR(1024) NOT NULL DEFAULT 'N/A'"
        );

        String tableColumns = String.join(", ", columnList);
        createTable(connection, "tables.sgp-transaction-history", tableColumns);
    }

    private synchronized void convertPSGPTable(@NotNull Connection connection) throws SQLException {
        ConfigurationManager configurationManager = this.plugin.getConfigurationManager();
        YamlConfiguration configuration = configurationManager.get("config.yml");
        String originalTableName = configuration.getString("tables.psgp-original");
        String convertedTableName = configuration.getString("tables.psgp-converted");

        String selectIdNotEmptyCode = getCommandFromSQL("select_id_not_empty", originalTableName);
        String deleteCode = getCommandFromSQL("delete_converted", convertedTableName)
                .replace("{select_not_empty}", selectIdNotEmptyCode);

        printDebug("Select Not Empty: " + selectIdNotEmptyCode);
        printDebug("Delete Converted: " + deleteCode);
        execute(connection, deleteCode);

        String selectNotEmptyCode = getCommandFromSQL("select_not_empty", originalTableName);
        Statement selectAllStatement = connection.createStatement();
        ResultSet selectAllResults = selectAllStatement.executeQuery(selectNotEmptyCode);

        String insertCode = getCommandFromSQL("insert_into_converted", convertedTableName);
        PreparedStatement insertPrepared = connection.prepareStatement(insertCode);

        while (selectAllResults.next()) {
            int id = selectAllResults.getInt("id");
            String shopName = selectAllResults.getString("name");
            String ownerUuid = selectAllResults.getString("ownerUuid");
            String shopItemsJson = selectAllResults.getString("shopItems");

            UUID playerId = getUUID(ownerUuid);
            if (playerId == null) {
                continue;
            }

            net.brcdev.playershopgui.shop.ShopItem[] shopItemArray = net.brcdev.playershopgui.util.GsonUtils
                    .getGson().fromJson(shopItemsJson, net.brcdev.playershopgui.shop.ShopItem[].class);
            if (shopItemArray == null || shopItemArray.length == 0) {
                continue;
            }

            JsonArray jsonArray = new JsonArray();
            for (ShopItem shopItem : shopItemArray) {
                ShopItemState state = shopItem.getState();
                if (state == ShopItemState.CANCELLED || state == ShopItemState.EXPIRED) {
                    continue;
                }

                long endTimestamp = shopItem.getEndTimestamp();
                if (endTimestamp <= System.currentTimeMillis()) {
                    continue;
                }

                ItemStack item = shopItem.getItemStack();
                JsonElement jsonElement = toJSON(item);
                if (jsonElement == null) {
                    continue;
                }

                double price = shopItem.getPrice();
                JsonObject jsonObject = jsonElement.getAsJsonObject();
                jsonObject.addProperty("price", price);
                jsonObject.addProperty("end-timestamp", endTimestamp);
                jsonArray.add(jsonObject);
            }

            if (jsonArray.size() == 0) {
                continue;
            }

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

    private @Nullable UUID getUUID(@NotNull String string) {
        try {
            return UUID.fromString(string);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private @Nullable JsonElement toJSON(@NotNull ItemStack item) {
        if (ItemUtility.isAir(item)) {
            return null;
        }


        CorePlugin corePlugin = JavaPlugin.getPlugin(CorePlugin.class);
        MultiVersionHandler multiVersionHandler = corePlugin.getMultiVersionHandler();
        ItemHandler itemHandler = multiVersionHandler.getItemHandler();

        String json = itemHandler.toNBT(item);
        return JsonParser.parseString(json);
    }

    private @NotNull String getCommandFromSQL(@NotNull String commandName, Object @NotNull ... replacements) {
        try {
            String fileName = ("commands/" + commandName + ".sql");
            InputStream jarFile = this.plugin.getResource(fileName);
            if (jarFile == null) {
                throw new IOException("'" + fileName + "' does not exist in the jar file.");
            }

            InputStreamReader jarFileReader = new InputStreamReader(jarFile);
            BufferedReader bufferedReader = new BufferedReader(jarFileReader);

            String currentLine;
            List<String> lineList = new ArrayList<>();
            while ((currentLine = bufferedReader.readLine()) != null) {
                lineList.add(currentLine);
            }

            String sqlCode = String.join("\n", lineList);
            return String.format(Locale.US, sqlCode, replacements);
        } catch (IOException ex) {
            Logger logger = this.plugin.getLogger();
            logger.log(Level.WARNING, "An error occurred while getting an SQL command:", ex);
            return "";
        }
    }

    private void printDebug(@NotNull String message) {
        ConfigurationManager configurationManager = this.plugin.getConfigurationManager();
        YamlConfiguration configuration = configurationManager.get("config.yml");
        if (!configuration.getBoolean("debug-mode", false)) {
            return;
        }

        Logger logger = this.plugin.getLogger();
        logger.info("[Debug] " + message);
    }
}
