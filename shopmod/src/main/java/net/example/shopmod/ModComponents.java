package net.example.shopmod;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENT_TYPES =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ShopMod.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> BALANCE =
            COMPONENT_TYPES.register("balance", () ->
                    DataComponentType.<Integer>builder().persistent(Codec.INT).build()
            );
}
