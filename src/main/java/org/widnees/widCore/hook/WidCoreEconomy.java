package org.widnees.widCore.hook;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.EconomyManager;

import java.util.List;

public class WidCoreEconomy implements Economy {

    private final Main plugin;
    private final EconomyManager economyManager;

    public WidCoreEconomy(Main plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
    }

    @Override
    public boolean isEnabled() { return plugin.getConfig().getBoolean("features.economy", false); }

    @Override
    public String getName() { return "WidCore Economy"; }

    @Override
    public boolean hasBankSupport() { return false; }

    @Override
    public int fractionalDigits() { return 2; }

    @Override
    public String format(double amount) {
        return String.format("%.2f", amount) + " " + plugin.getLanguageManager().getMessage("economy.symbol");
    }

    @Override
    public String currencyNamePlural() { return ""; }

    @Override
    public String currencyNameSingular() { return ""; }

    @Override
    public boolean hasAccount(OfflinePlayer player) { return true; }

    @Override
    public boolean hasAccount(String playerName) { return true; }

    @Override
    public boolean hasAccount(String playerName, String worldName) { return hasAccount(playerName); }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) { return hasAccount(player); }

    @Override
    public double getBalance(OfflinePlayer player) { return economyManager.getBalance(player); }

    @Override
    public double getBalance(String playerName) { return 0; }

    @Override
    public double getBalance(String playerName, String world) { return getBalance(playerName); }

    @Override
    public double getBalance(OfflinePlayer player, String world) { return getBalance(player); }

    @Override
    public boolean has(OfflinePlayer player, double amount) { return economyManager.has(player, amount); }

    @Override
    public boolean has(String playerName, double amount) { return false; }

    @Override
    public boolean has(String playerName, String worldName, double amount) { return has(playerName, amount); }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) { return has(player, amount); }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        if (economyManager.withdraw(player, amount)) {
            return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
        }
        return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, plugin.getLanguageManager().getMessage("economy.insufficient-funds"));
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "İsim ile işlem desteklenmiyor"); }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) { return withdrawPlayer(playerName, amount); }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) { return withdrawPlayer(player, amount); }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        economyManager.deposit(player, amount);
        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "İsim ile işlem desteklenmiyor"); }

    @Override
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) { return depositPlayer(playerName, amount); }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) { return depositPlayer(player, amount); }

    @Override public EconomyResponse createBank(String name, String player) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banka desteklenmiyor"); }
    @Override public EconomyResponse createBank(String name, OfflinePlayer player) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banka desteklenmiyor"); }
    @Override public EconomyResponse deleteBank(String name) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banka desteklenmiyor"); }
    @Override public EconomyResponse bankBalance(String name) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banka desteklenmiyor"); }
    @Override public EconomyResponse bankHas(String name, double amount) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banka desteklenmiyor"); }
    @Override public EconomyResponse bankWithdraw(String name, double amount) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banka desteklenmiyor"); }
    @Override public EconomyResponse bankDeposit(String name, double amount) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banka desteklenmiyor"); }
    @Override public EconomyResponse isBankOwner(String name, String playerName) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banka desteklenmiyor"); }
    @Override public EconomyResponse isBankOwner(String name, OfflinePlayer player) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banka desteklenmiyor"); }
    @Override public EconomyResponse isBankMember(String name, String playerName) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banka desteklenmiyor"); }
    @Override public EconomyResponse isBankMember(String name, OfflinePlayer player) { return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Banka desteklenmiyor"); }
    @Override public List<String> getBanks() { return null; }

    @Override public boolean createPlayerAccount(String playerName) { return false; }
    @Override public boolean createPlayerAccount(OfflinePlayer player) { economyManager.createAccount(player); return true; }
    @Override public boolean createPlayerAccount(String playerName, String worldName) { return false; }
    @Override public boolean createPlayerAccount(OfflinePlayer player, String worldName) { return createPlayerAccount(player); }
}