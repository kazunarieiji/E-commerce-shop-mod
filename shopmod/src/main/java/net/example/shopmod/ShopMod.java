package net.example.shopmod;

import net.example.shopmod.client.CartScreen;
import net.example.shopmod.client.LoadingScreen;
import net.example.shopmod.client.ShopScreen;
import net.example.shopmod.config.ShopConfig;
import net.example.shopmod.network.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@Mod(ShopMod.MOD_ID)
public class ShopMod {
    public static final String MOD_ID = "shopmod";

    public ShopMod(IEventBus modEventBus) {
        ModMenuTypes.MENUS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModComponents.COMPONENT_TYPES.register(modEventBus);

        modEventBus.addListener(this::registerPayloads);
        modEventBus.addListener(this::clientSetup);

        NeoForge.EVENT_BUS.addListener(this::registerCommands);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToServer(ShopCartActionPayload.TYPE, ShopCartActionPayload.STREAM_CODEC, ShopCartActionPayload::handleServer);
        registrar.playToServer(ShopBuyCardPayload.TYPE, ShopBuyCardPayload.STREAM_CODEC, ShopBuyCardPayload::handleServer);
        registrar.playToServer(OpenCartPayload.TYPE, OpenCartPayload.STREAM_CODEC, OpenCartPayload::handleServer);
        registrar.playToServer(OpenShopPayload.TYPE, OpenShopPayload.STREAM_CODEC, OpenShopPayload::handleServer);
        registrar.playBidirectional(
                RequestReadyPayload.TYPE,
                RequestReadyPayload.STREAM_CODEC,
                new DirectionalPayloadHandler<>(
                        RequestReadyPayload::handleClient,
                        RequestReadyPayload::handleServer
                )
        );
        registrar.playToClient(ShopSyncPayload.TYPE, ShopSyncPayload.STREAM_CODEC, ShopSyncPayload::handleClient);
        registrar.playToClient(ShopBalancePayload.TYPE, ShopBalancePayload.STREAM_CODEC, ShopBalancePayload::handleClient);
        registrar.playToClient(ReadyResponsePayload.TYPE, ReadyResponsePayload.STREAM_CODEC, ReadyResponsePayload::handleClient);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        ShopCommand.register(event.getDispatcher(), event.getBuildContext());
    }

    private void onServerStarting(ServerStartingEvent event) {
        ShopConfig.initAsync(event.getServer().overworld());
    }

    private void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {});
    }

    @Mod(value = MOD_ID, dist = Dist.CLIENT)
    public static class ClientModEvents {
        public ClientModEvents(IEventBus modEventBus) {
            modEventBus.addListener((RegisterMenuScreensEvent event) -> {
                event.register(ModMenuTypes.SHOP_MENU.get(), ShopScreen::new);
                event.register(ModMenuTypes.CART_MENU.get(), CartScreen::new);
            });
        }
    }
}
