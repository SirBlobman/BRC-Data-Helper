DELETE FROM `%s` WHERE `id` NOT IN ({select_not_empty});
