package net.example.shopmod.client;

import java.util.LinkedHashMap;
import java.util.Map;

public class ClientCartData {
    private static final Map<Integer, Integer> cart = new LinkedHashMap<>();
    public static Map<Integer, Integer> getCart() { return cart; }
    public static void addToCart(int entryIndex, int qty) { cart.merge(entryIndex, qty, Integer::sum); }
    public static void setItem(int entryIndex, int qty) {
        if (qty <= 0) cart.remove(entryIndex);
        else cart.put(entryIndex, qty);
    }
    public static void clear() { cart.clear(); }
}
