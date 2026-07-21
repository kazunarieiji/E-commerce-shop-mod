package net.example.shopmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.example.shopmod.config.ShopConfig;
import net.example.shopmod.item.CardItem;
import net.example.shopmod.network.RequestReadyPayload;
import net.example.shopmod.network.ShopSyncPayload;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class ShopCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(literal("shop")
                .executes(ShopCommand::openShop)
                .then(literal("reload").requires(src -> src.hasPermission(2)).executes(ShopCommand::reloadConfig))
                .then(literal("setprice").requires(src -> src.hasPermission(2))
                        .then(argument("item", ItemArgument.item(buildContext))
                                .then(argument("buy", IntegerArgumentType.integer(1))
                                        .then(argument("sell", IntegerArgumentType.integer(0))
                                                .executes(ShopCommand::setPrice)))))
        );
        dispatcher.register(
                literal("coins")
                        .requires(src -> src.hasPermission(2))
                        .then(literal("give")
                                .then(argument("player", EntityArgument.player())
                                        .then(argument("amount", IntegerArgumentType.integer(1))
                                                .executes(ShopCommand::giveCoins))))
                        .then(literal("balance")
                                .then(argument("player", EntityArgument.player())
                                        .executes(ShopCommand::checkBalance)))
        );
        dispatcher.register(
                literal("card")
                        .requires(src -> src.hasPermission(2))
                        .then(literal("give")
                                .then(argument("player", EntityArgument.player())
                                        .executes(ctx -> giveCard(ctx, 0))
                                        .then(argument("amount", IntegerArgumentType.integer(0))
                                                .executes(ctx -> giveCard(ctx, IntegerArgumentType.getInteger(ctx, "amount"))))))
        );
        dispatcher.register(literal("cart").executes(ShopCommand::openCart));
    }

    private static int openShop(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;

        if (!ShopConfig.isReady()) {
            // Not ready – ask client to show loading screen and start polling
            PacketDistributor.sendToPlayer(player, new RequestReadyPayload());
            player.displayClientMessage(Component.literal("§ePrices are being generated – opening loading screen..."), true);
            return 1;
        }

        // Ready – open shop directly
        List<ShopEntry> entries = ShopEntries.get();
        PacketDistributor.sendToPlayer(player, new ShopSyncPayload(entries));

        player.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() { return Component.literal("e-Commerce Market"); }
            @Override public AbstractContainerMenu createMenu(int windowId, Inventory inv, Player p) {
                return new ShopMenu(windowId, inv);
            }
        });
        BalanceUtil.sync(player);
        return 1;
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        ShopConfig.reloadAsync(ctx.getSource().getLevel());
        ctx.getSource().sendSuccess(() -> Component.literal("§a[ShopMod] Reload started in background."), true);
        return 1;
    }

    private static int setPrice(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Item item = ItemArgument.getItem(ctx, "item").getItem();
        int buy = IntegerArgumentType.getInteger(ctx, "buy");
        int sell = IntegerArgumentType.getInteger(ctx, "sell");

        String itemId = BuiltInRegistries.ITEM.getKey(item).toString();
        ShopConfig.setPrice(itemId, buy, sell);

        ShopSyncPayload syncPayload = new ShopSyncPayload(ShopEntries.get());
        for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, syncPayload);
        }

        ctx.getSource().sendSuccess(() -> Component.literal("§a[ShopMod] Updated " + itemId + " -> Buy: " + buy + " Coins, Sell: " + sell + " Coins!"), true);
        return 1;
    }

    private static int openCart(CommandContext<CommandSourceStack> ctx) {
        if (!(ctx.getSource().getEntity() instanceof ServerPlayer player)) return 0;
        player.openMenu(new MenuProvider() {
            @Override public Component getDisplayName() { return Component.literal("Shopping Cart"); }
            @Override public AbstractContainerMenu createMenu(int windowId, Inventory inv, Player p) {
                return new CartMenu(windowId, inv);
            }
        });
        return 1;
    }

    private static int giveCoins(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        int amount = IntegerArgumentType.getInteger(ctx, "amount");
        BalanceUtil.setBalance(target, BalanceUtil.getBalance(target) + amount);
        BalanceUtil.sync(target);
        ctx.getSource().sendSuccess(() ->
                Component.literal("Deposited " + amount + " Coins to " + target.getName().getString() + "'s e-Card (New Balance: " + BalanceUtil.getBalance(target) + " Coins)"), true);
        return 1;
    }

    private static int checkBalance(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        ctx.getSource().sendSuccess(() ->
                Component.literal(target.getName().getString() + " has " + BalanceUtil.getBalance(target) + " Coins"), false);
        return 1;
    }

    private static int giveCard(CommandContext<CommandSourceStack> ctx, int startingBalance) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
        ItemStack card = new ItemStack(ModItems.CARD.get());
        CardItem.setBalance(card, startingBalance);
        if (!target.getInventory().add(card)) target.drop(card, false);
        ctx.getSource().sendSuccess(() ->
                Component.literal("Issued a Premium e-Card loaded with " + startingBalance + " Coins to " + target.getName().getString()), true);
        return 1;
    }
}
