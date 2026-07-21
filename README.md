# 🛒 Universal E-Commerce Shop

[![Platform](https://shields.io)](https://neoforged.net)
[![License](https://shields.io)](LICENSE)

A powerful, standalone economy and shop system for **NeoForge 1.21.1**. Featuring an intuitive custom GUI menu, this mod automatically detects and dynamically prices any item in the game—including vanilla and modded content—without requiring tedious configuration.

---

## ✨ Features

* **Universal Compatibility:** Automatically scans, detects, and supports every single item from vanilla Minecraft and any installed mods.
* **Sleek Custom GUI:** Clean, responsive user interface for buying and selling items smoothly.
* **Instant Command Access:** Players can open the market anytime, anywhere using the `/shop` command.
* **Built-in Standalone Economy:** Fully independent currency system. No external economy dependencies or libraries required.
* **Infinite Supply & Demand:** Zero global stock limits. Players can trade endlessly without crashing the server's economy.
* **Live Admin Syncing:** Administrators can update pricing or blacklists directly on the server. Changes push to online clients instantly via custom network packets without requiring a game restart.

---

## ⚙️ Requirements & Installation

* **Mod Loader:** NeoForge 1.21.1
* **Side Compatibility:** Required on **both** the Server and Client sides (custom GUI elements will not render on vanilla clients).

1. Download the latest `.jar` release.
2. Drop the file into the `mods` folder of both your server and your Minecraft client directory.
3. Launch the game!

---

## 💻 Commands

### Player Commands
* `/shop` - Opens the custom e-commerce shop interface.
* `/balance` - Displays your current currency holdings.

### Admin Commands (Requires OP / Permission Level 2+)
* `/shop reload` - Reloads the configuration files and pushes live sync data to all active clients.
* `/shop blacklist add <item_id>` - Excludes a specific item from being bought or sold in the shop.
* `/shop blacklist remove <item_id>` - Removes an item from the blacklist.

---

## 📄 License

This project is open-source and licensed under the **GNU Lesser General Public License v3.0 (LGPLv3)**. See the [LICENSE](LICENSE) file for the full text. 

Under this license, developers can link against this mod, but any direct modifications to this source code must also be made public under the LGPLv3.
