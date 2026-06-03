package org.widnees.widCore.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.widnees.widCore.Main;
import org.widnees.widCore.manager.TextParser;

import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class ItemEffectManager {
    private final Main plugin;
    private final NamespacedKey effectKey;
    private static final Gson gson = new GsonBuilder().create();
    public static String EFFECT_MENU_TITLE;

    private final Map<UUID, ItemStack> editingItems = new HashMap<>();
    private final Map<UUID, Map<PotionEffectType, EffectData>> managedPlayerEffects = new HashMap<>();

    public ItemEffectManager(Main plugin) {
        this.plugin = plugin;
        this.effectKey = new NamespacedKey((Plugin) plugin, "item_effects_map");
        EFFECT_MENU_TITLE = plugin.getLanguageManager().getMessage("itemeffect.menu-title");
    }

    public void updatePlayerEffects(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) return;

        Map<PotionEffectType, EffectData> requiredEffects = collectRequiredEffectsFromEquipment(player);
        Map<PotionEffectType, EffectData> previouslyManagedEffects = managedPlayerEffects.getOrDefault(player.getUniqueId(), new HashMap<>());

        removeObsoleteEffects(player, requiredEffects, previouslyManagedEffects);
        applyOrUpgradeEffects(player, requiredEffects, previouslyManagedEffects);

        managedPlayerEffects.put(player.getUniqueId(), requiredEffects);
    }

    private Map<PotionEffectType, EffectData> collectRequiredEffectsFromEquipment(Player player) {
        Map<PotionEffectType, EffectData> requiredEffects = new HashMap<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.name().equals("BODY")) continue; 

            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType().isAir()) continue;

            Map<String, EffectData> itemEffectsMap = getDataFromItem(item);
            if (itemEffectsMap.isEmpty()) continue;

            itemEffectsMap.forEach((effectName, data) -> {
                if (data.level <= 0) return;
                if (!data.activeSlots.contains(slot)) return;
                PotionEffectType type = PotionEffectType.getByName(effectName);
                if (type == null) return;
                requiredEffects.merge(type, data, (oldData, newData) -> newData.level > oldData.level ? newData : oldData);
            });
        }
        return requiredEffects;
    }

    private void removeObsoleteEffects(Player player,
                                       Map<PotionEffectType, EffectData> required,
                                       Map<PotionEffectType, EffectData> previous) {
        for (PotionEffectType typeToRemove : previous.keySet()) {
            if (!required.containsKey(typeToRemove)) {
                player.removePotionEffect(typeToRemove);
            }
        }
    }

    private void applyOrUpgradeEffects(Player player,
                                       Map<PotionEffectType, EffectData> required,
                                       Map<PotionEffectType, EffectData> previous) {
        required.forEach((type, data) -> {
            EffectData prev = previous.get(type);
            if (prev == null || prev.level != data.level) {
                player.addPotionEffect(new PotionEffect(type, Integer.MAX_VALUE, data.level - 1, data.ambient, data.particles, data.icon));
            }
        });
    }

    public void cleanupEditor(UUID playerUuid) {
        editingItems.remove(playerUuid);
    }

    public void cleanupPlayerSession(UUID playerUuid) {
        managedPlayerEffects.remove(playerUuid);
    }

    public void openEffectMenu(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            Main.sendMessage(plugin, player, plugin.getLanguageManager().getMessage("itemeffect.need-item"));
            return;
        }
        Inventory menu = Bukkit.createInventory((InventoryHolder) player, 54, EFFECT_MENU_TITLE);
        editingItems.put(player.getUniqueId(), item);
        updateFullMenu(menu, item);
        player.openInventory(menu);
    }

    private void updateFullMenu(Inventory menu, ItemStack itemToEdit) {
        menu.clear();
        Map<String, EffectData> currentEffectsMap = getDataFromItem(itemToEdit);

        fillEffectEntries(menu, currentEffectsMap);

        Set<EquipmentSlot> activeSlots = getRepresentativeActiveSlots(currentEffectsMap);
        fillSlotToggles(menu, activeSlots);
    }

    private void fillEffectEntries(Inventory menu, Map<String, EffectData> currentEffectsMap) {
        List<PotionEffectType> sortedEffects = Arrays.stream(PotionEffectType.values())
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(PotionEffectType::getName))
                .collect(Collectors.toList());

        for (int i = 0; i < sortedEffects.size() && i < 45; i++) {
            PotionEffectType type = sortedEffects.get(i);
            EffectData effectData = currentEffectsMap.getOrDefault(type.getName(), new EffectData());
            menu.setItem(i, createPotionItem(type, effectData));
        }
    }

    private Set<EquipmentSlot> getRepresentativeActiveSlots(Map<String, EffectData> currentEffectsMap) {
        EffectData representativeData = currentEffectsMap.isEmpty() ? new EffectData() : currentEffectsMap.values().iterator().next();
        return representativeData.activeSlots;
    }

    private void fillSlotToggles(Inventory menu, Set<EquipmentSlot> activeSlots) {
        menu.setItem(47, createSlotItem(Material.DIAMOND_SWORD, plugin.getLanguageManager().getMessage("itemeffect.right-hand"), EquipmentSlot.HAND, activeSlots));
        menu.setItem(48, createSlotItem(Material.SHIELD, plugin.getLanguageManager().getMessage("itemeffect.left-hand"), EquipmentSlot.OFF_HAND, activeSlots));
        menu.setItem(49, createSlotItem(Material.DIAMOND_HELMET, plugin.getLanguageManager().getMessage("itemeffect.helmet"), EquipmentSlot.HEAD, activeSlots));
        menu.setItem(50, createSlotItem(Material.DIAMOND_CHESTPLATE, plugin.getLanguageManager().getMessage("itemeffect.chestplate"), EquipmentSlot.CHEST, activeSlots));
        menu.setItem(51, createSlotItem(Material.DIAMOND_LEGGINGS, plugin.getLanguageManager().getMessage("itemeffect.leggings"), EquipmentSlot.LEGS, activeSlots));
        menu.setItem(52, createSlotItem(Material.DIAMOND_BOOTS, plugin.getLanguageManager().getMessage("itemeffect.boots"), EquipmentSlot.FEET, activeSlots));
    }

    public void handleClick(Player player, int slot, ItemStack clickedItem, ClickType clickType) {
        ItemStack itemToEdit = editingItems.get(player.getUniqueId());
        if (itemToEdit == null) return;

        Map<String, EffectData> effectsMap = getDataFromItem(itemToEdit);
        if (slot >= 45) {
            handleSlotToggleClick(player, clickedItem, effectsMap, itemToEdit);
            return;
        }

        handleEffectClick(player, slot, clickedItem, clickType, effectsMap, itemToEdit);
    }

    private void handleSlotToggleClick(Player player,
                                       ItemStack clickedItem,
                                       Map<String, EffectData> effectsMap,
                                       ItemStack itemToEdit) {
        EquipmentSlot equipmentSlot = getSlotFromMaterial(clickedItem.getType());
        if (equipmentSlot == null) return;

        EffectData tempEffectData = effectsMap.isEmpty() ? new EffectData() : effectsMap.values().iterator().next();
        toggleSlot(tempEffectData, equipmentSlot);
        syncActiveSlotsAcrossEffects(effectsMap, tempEffectData.activeSlots);

        saveDataToItem(itemToEdit, effectsMap);
        updateFullMenu(player.getOpenInventory().getTopInventory(), itemToEdit);
    }

    private void toggleSlot(EffectData data, EquipmentSlot equipmentSlot) {
        if (data.activeSlots.contains(equipmentSlot)) {
            data.activeSlots.remove(equipmentSlot);
        } else {
            data.activeSlots.add(equipmentSlot);
        }
    }

    private void syncActiveSlotsAcrossEffects(Map<String, EffectData> effectsMap, Set<EquipmentSlot> sourceSlots) {
        for (EffectData d : effectsMap.values()) {
            d.activeSlots = new HashSet<>(sourceSlots);
        }
    }

    private void handleEffectClick(Player player,
                                   int slot,
                                   ItemStack clickedItem,
                                   ClickType clickType,
                                   Map<String, EffectData> effectsMap,
                                   ItemStack itemToEdit) {
        if (clickedItem.getType() != Material.POTION) return;
        PotionMeta potionMeta = (PotionMeta) clickedItem.getItemMeta();
        if (potionMeta == null || !potionMeta.hasCustomEffects()) return;

        PotionEffectType effectType = potionMeta.getCustomEffects().get(0).getType();
        EffectData data = effectsMap.computeIfAbsent(effectType.getName(), k -> new EffectData());

        ensureDefaultSlotsIfSingleEffect(effectsMap, data);
        applyClickToEffectData(clickType, data);
        removeIfLevelZero(effectsMap, effectType, data);

        saveDataToItem(itemToEdit, effectsMap);
        player.getOpenInventory().getTopInventory().setItem(slot, createPotionItem(effectType, data));
    }

    private void ensureDefaultSlotsIfSingleEffect(Map<String, EffectData> effectsMap, EffectData data) {
        if (effectsMap.size() == 1) {
            data.activeSlots = new HashSet<>(Arrays.asList(EquipmentSlot.values()));
            data.activeSlots.remove(EquipmentSlot.CHEST);
        }
    }

    private void applyClickToEffectData(ClickType clickType, EffectData data) {
        switch (clickType) {
            case RIGHT:
                if (data.level < 256) data.level++;
                break;
            case LEFT:
                if (data.level > 0) data.level--;
                break;
            case SHIFT_LEFT:
                data.particles = !data.particles;
                break;
            case SHIFT_RIGHT:
                data.ambient = !data.ambient;
                break;
            case MIDDLE:
                data.icon = !data.icon;
                break;
            default:

                break;
        }
    }

    private void removeIfLevelZero(Map<String, EffectData> effectsMap, PotionEffectType effectType, EffectData data) {
        if (data.level == 0) {
            effectsMap.remove(effectType.getName());
        }
    }

    private Map<String, EffectData> getDataFromItem(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(this.effectKey, PersistentDataType.STRING)) {
            return new HashMap<>();
        }
        String json = meta.getPersistentDataContainer().get(this.effectKey, PersistentDataType.STRING);
        Type type = new TypeToken<Map<String, EffectData>>() {}.getType();
        Map<String, EffectData> map = gson.fromJson(json, type);
        return map == null ? new HashMap<>() : map;
    }

    private void saveDataToItem(ItemStack item, Map<String, EffectData> data) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        Map<String, EffectData> finalData = data.entrySet().stream()
                .filter(e -> e.getValue().level > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        persistJson(meta, finalData);
        cleanupLore(meta);
        item.setItemMeta(meta);
    }

    private void persistJson(ItemMeta meta, Map<String, EffectData> finalData) {
        if (finalData.isEmpty()) {
            meta.getPersistentDataContainer().remove(this.effectKey);
        } else {
            String json = gson.toJson(finalData);
            meta.getPersistentDataContainer().set(this.effectKey, PersistentDataType.STRING, json);
        }
    }

    private void cleanupLore(ItemMeta meta) {
        if (!meta.hasLore()) return;
        List<Component> currentLore = meta.lore();
        if (currentLore == null) return;

        currentLore.removeIf(line -> PlainTextComponentSerializer.plainText().serialize(line).startsWith("Effects:"));
        if (currentLore.isEmpty()) {
            meta.lore(null);
        } else {
            meta.lore(currentLore);
        }
    }

    private ItemStack createPotionItem(PotionEffectType type, EffectData data) {
        boolean isActive = data.level > 0;
        ItemStack potionItem = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potionItem.getItemMeta();
        meta.addCustomEffect(new PotionEffect(type, 1, 0), true);
        meta.addItemFlags(ItemFlag.HIDE_POTION_EFFECTS);
        meta.displayName(TextParser.parse((isActive ? "&a&l" : "&c&l") + type.getName()));

        List<Component> lore = buildPotionLore(data, isActive);
        if (isActive) {
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        meta.lore(lore);
        potionItem.setItemMeta(meta);
        return potionItem;
    }

    private List<Component> buildPotionLore(EffectData data, boolean isActive) {
        List<Component> lore = new ArrayList<>();
        if (isActive) {
            lore.add(TextParser.parse(plugin.getLanguageManager().getMessage("itemeffect.level").replace("%level%", String.valueOf(data.level))));
        } else {
            lore.add(TextParser.parse(plugin.getLanguageManager().getMessage("itemeffect.level-off")));
        }

        String activeStatus = plugin.getLanguageManager().getMessage("itemeffect.active");
        String passiveStatus = plugin.getLanguageManager().getMessage("itemeffect.passive");
        lore.add(TextParser.parse(plugin.getLanguageManager().getMessage("itemeffect.toggle-particles").replace("%status%", data.particles ? activeStatus : passiveStatus)));
        lore.add(TextParser.parse(plugin.getLanguageManager().getMessage("itemeffect.toggle-ambient").replace("%status%", data.ambient ? activeStatus : passiveStatus)));
        lore.add(TextParser.parse(plugin.getLanguageManager().getMessage("itemeffect.toggle-icon").replace("%status%", data.icon ? activeStatus : passiveStatus)));
        lore.add(Component.text(""));
        if (!isActive) {
            lore.add(TextParser.parse(plugin.getLanguageManager().getMessage("itemeffect.activate")));
        }
        return lore;
    }

    private ItemStack createSlotItem(Material material, String name, EquipmentSlot slot, Set<EquipmentSlot> activeSlots) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        boolean isActive = activeSlots.contains(slot);
        meta.displayName(TextParser.parse((isActive ? "&a" : "&c") + name));
        String status = isActive ? plugin.getLanguageManager().getMessage("itemeffect.active") : plugin.getLanguageManager().getMessage("itemeffect.passive");
        meta.lore(Collections.singletonList(TextParser.parse(plugin.getLanguageManager().getMessage("itemeffect.status").replace("%status%", status))));
        if (isActive) {
            meta.addEnchant(Enchantment.LUCK, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private EquipmentSlot getSlotFromMaterial(Material material) {
        switch (material) {
            case DIAMOND_SWORD:
                return EquipmentSlot.HAND;
            case SHIELD:
                return EquipmentSlot.OFF_HAND;
            case DIAMOND_HELMET:
                return EquipmentSlot.HEAD;
            case DIAMOND_CHESTPLATE:
                return EquipmentSlot.CHEST;
            case DIAMOND_LEGGINGS:
                return EquipmentSlot.LEGS;
            case DIAMOND_BOOTS:
                return EquipmentSlot.FEET;
            default:
                return null;
        }
    }

    public boolean itemHasEffects(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(this.effectKey, PersistentDataType.STRING);
    }

    public static class EffectData {
        public int level = 0;
        public boolean particles = true;
        public boolean ambient = true;
        public boolean icon = true;
        public Set<EquipmentSlot> activeSlots = new HashSet<>(Arrays.asList(EquipmentSlot.values()));
    }
        @SuppressWarnings("unused")
    private static final String __Wf7c3e9 = "\u0077\u0069\u0064\u006e" + "\u0065\u0065\u0073";

}
