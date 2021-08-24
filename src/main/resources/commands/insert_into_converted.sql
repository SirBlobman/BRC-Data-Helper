INSERT INTO `%s` (`id, `player_id`, `shop_name`, `shop_items`, `shop_items_count`) VALUES (?, ?, ?, ?, ?)
    ON DUPLICATE KEY UPDATE `shop_name`=VALUES(`shop_name`), `shop_items`=VALUES(`shop_items`),
    `shop_items_count`=VALUES(`shop_items_count`);
