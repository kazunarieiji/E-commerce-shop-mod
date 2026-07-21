package net.example.shopmod.client;

import net.example.shopmod.ShopEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClientShopData {
    private static int balance = 0;
    private static List<ShopEntry> entries = new ArrayList<>();

    public static int getBalance() { return balance; }
    public static void setBalance(int newBalance) { balance = newBalance; }

    public static synchronized List<ShopEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }
    public static synchronized void setEntries(List<ShopEntry> newEntries) {
        entries = new ArrayList<>(newEntries);
    }
}
