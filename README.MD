# BRC Data Helper

A plugin that helps out with databases from BRC plugins.  
*Please send me suggestions for new features or feature changes.*

## Requirements
- Java 17
- [Spigot](https://www.spigotmc.org/) or [Paper](https://papermc.io/) 1.19.4
- [ShopGUIPlus](https://www.spigotmc.org/resources/6515/) 1.78.0 or higher.
- [PlayerShopGUIPlus](https://www.spigotmc.org/resources/37707/) 1.26.3 or higher.
- [BlueSlimeCore](https://www.spigotmc.org/resources/83189/) 2.7.0 or higher.
- A MySQL database previously linked to PlayerShopGUIPlus.

## Features
- Convert original PlayerShopGUIPlus database with base64-binary items into a different table as json items.
- Purchase history table that stores information when players buy items from other players.
- Creation history table that stores information when players add items to their own shop.
- Transaction history table for ShopGUIPlus when players sell or buy items.
