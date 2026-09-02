package org.widnees.widCore.manager;

import org.bukkit.Material;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Axolotl;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Cat;
import org.bukkit.entity.ChestedHorse;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Fox;
import org.bukkit.entity.Goat;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.Horse;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.MushroomCow;
import org.bukkit.entity.Panda;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Steerable;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TropicalFish;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zoglin;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.inventory.AbstractHorseInventory;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.HorseInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.LlamaInventory;
import org.bukkit.material.Colorable;

public final class MobSimilarity {

    public static final class Options {
        public boolean matchAge = true;
        public boolean matchVillagerProfession = true;
        public boolean matchVillagerType = true;
        public boolean matchColorAndVariant = true;
        public boolean matchSlimeSize = true;
        public boolean matchCreeperPowered = true;
        public boolean matchEquipment = false;
    }

    private MobSimilarity() {
    }

    public static boolean isSimilar(LivingEntity first, LivingEntity second) {
        return isSimilar(first, second, new Options());
    }

    public static boolean isSimilar(LivingEntity first, LivingEntity second, Options options) {
        if (first == null || second == null || options == null) {
            return false;
        }
        if (first.getType() != second.getType()) {
            return false;
        }
        if (options.matchAge && first instanceof Ageable && second instanceof Ageable
                && ((Ageable) first).isAdult() != ((Ageable) second).isAdult()) {
            return false;
        }
        if (options.matchEquipment && !equipmentMatches(first.getEquipment(), second.getEquipment())) {
            return false;
        }
        if (first instanceof Tameable && second instanceof Tameable
                && ((Tameable) first).isTamed() != ((Tameable) second).isTamed()) {
            return false;
        }
        if (options.matchColorAndVariant && first instanceof Colorable && second instanceof Colorable
                && ((Colorable) first).getColor() != ((Colorable) second).getColor()) {
            return false;
        }
        if (options.matchColorAndVariant && first instanceof Sheep && second instanceof Sheep
                && ((Sheep) first).isSheared() != ((Sheep) second).isSheared()) {
            return false;
        }
        if (options.matchColorAndVariant && first instanceof Wolf && second instanceof Wolf
                && ((Wolf) first).getCollarColor() != ((Wolf) second).getCollarColor()) {
            return false;
        }
        return compareSpecialTypes(first, second, options);
    }
    private static boolean compareSpecialTypes(LivingEntity first, LivingEntity second, Options options) {
        if (options.matchColorAndVariant) {
            if (first instanceof Cat && second instanceof Cat) {
                Cat firstCat = (Cat) first;
                Cat secondCat = (Cat) second;
                if (firstCat.getCatType() != secondCat.getCatType()
                        || firstCat.getCollarColor() != secondCat.getCollarColor()) {
                    return false;
                }
            }
            if (first instanceof Parrot && second instanceof Parrot
                    && ((Parrot) first).getVariant() != ((Parrot) second).getVariant()) {
                return false;
            }
            if (first instanceof Fox && second instanceof Fox
                    && ((Fox) first).getFoxType() != ((Fox) second).getFoxType()) {
                return false;
            }
            if (first instanceof Rabbit && second instanceof Rabbit
                    && ((Rabbit) first).getRabbitType() != ((Rabbit) second).getRabbitType()) {
                return false;
            }
            if (first instanceof Axolotl && second instanceof Axolotl
                    && ((Axolotl) first).getVariant() != ((Axolotl) second).getVariant()) {
                return false;
            }
            if (first instanceof Panda && second instanceof Panda) {
                Panda firstPanda = (Panda) first;
                Panda secondPanda = (Panda) second;
                if (firstPanda.getMainGene() != secondPanda.getMainGene()
                        || firstPanda.getHiddenGene() != secondPanda.getHiddenGene()) {
                    return false;
                }
            }
            if (first instanceof TropicalFish && second instanceof TropicalFish) {
                TropicalFish firstFish = (TropicalFish) first;
                TropicalFish secondFish = (TropicalFish) second;
                if (firstFish.getPattern() != secondFish.getPattern()
                        || firstFish.getBodyColor() != secondFish.getBodyColor()
                        || firstFish.getPatternColor() != secondFish.getPatternColor()) {
                    return false;
                }
            }
            if (first instanceof MushroomCow && second instanceof MushroomCow
                    && ((MushroomCow) first).getVariant() != ((MushroomCow) second).getVariant()) {
                return false;
            }
        }
        if (first instanceof Bee && second instanceof Bee) {
            Bee firstBee = (Bee) first;
            Bee secondBee = (Bee) second;
            if (firstBee.hasNectar() != secondBee.hasNectar()
                    || firstBee.hasStung() != secondBee.hasStung()) {
                return false;
            }
        }
        return compareHorsesAndMonsters(first, second, options);
    }
    private static boolean compareHorsesAndMonsters(LivingEntity first, LivingEntity second, Options options) {
        if (options.matchColorAndVariant) {
            if (first instanceof Horse && second instanceof Horse) {
                Horse firstHorse = (Horse) first;
                Horse secondHorse = (Horse) second;
                if (firstHorse.getColor() != secondHorse.getColor()
                        || firstHorse.getStyle() != secondHorse.getStyle()) {
                    return false;
                }
            }
            if (first instanceof Llama && second instanceof Llama) {
                Llama firstLlama = (Llama) first;
                Llama secondLlama = (Llama) second;
                if (firstLlama.getColor() != secondLlama.getColor()
                        || firstLlama.getStrength() != secondLlama.getStrength()) {
                    return false;
                }
            }
            if (first instanceof Goat && second instanceof Goat
                    && ((Goat) first).isScreaming() != ((Goat) second).isScreaming()) {
                return false;
            }
            if (first instanceof Snowman && second instanceof Snowman
                    && ((Snowman) first).isDerp() != ((Snowman) second).isDerp()) {
                return false;
            }
            if (first instanceof IronGolem && second instanceof IronGolem
                    && ((IronGolem) first).isPlayerCreated() != ((IronGolem) second).isPlayerCreated()) {
                return false;
            }
            if (first instanceof Enderman && second instanceof Enderman
                    && !carriedBlockMatches((Enderman) first, (Enderman) second)) {
                return false;
            }
        }
        if (first instanceof ChestedHorse && second instanceof ChestedHorse
                && ((ChestedHorse) first).isCarryingChest() != ((ChestedHorse) second).isCarryingChest()) {
            return false;
        }
        if (options.matchEquipment) {
            if (first instanceof AbstractHorse && second instanceof AbstractHorse
                    && !horseInventoryMatches((AbstractHorse) first, (AbstractHorse) second)) {
                return false;
            }
            if (first instanceof Steerable && second instanceof Steerable
                    && ((Steerable) first).hasSaddle() != ((Steerable) second).hasSaddle()) {
                return false;
            }
        }
        if (options.matchCreeperPowered && first instanceof Creeper && second instanceof Creeper
                && ((Creeper) first).isPowered() != ((Creeper) second).isPowered()) {
            return false;
        }
        if (options.matchSlimeSize) {
            if (first instanceof Slime && second instanceof Slime
                    && ((Slime) first).getSize() != ((Slime) second).getSize()) {
                return false;
            }
            if (first instanceof Phantom && second instanceof Phantom
                    && ((Phantom) first).getSize() != ((Phantom) second).getSize()) {
                return false;
            }
        }
        return compareVillagersAndUndead(first, second, options);
    }
    private static boolean compareVillagersAndUndead(LivingEntity first, LivingEntity second, Options options) {
        if (first instanceof Villager && second instanceof Villager) {
            Villager firstVillager = (Villager) first;
            Villager secondVillager = (Villager) second;
            if (options.matchVillagerProfession
                    && firstVillager.getProfession() != secondVillager.getProfession()) {
                return false;
            }
            if (options.matchVillagerType
                    && firstVillager.getVillagerType() != secondVillager.getVillagerType()) {
                return false;
            }
        }
        if (first instanceof ZombieVillager && second instanceof ZombieVillager) {
            ZombieVillager firstZombie = (ZombieVillager) first;
            ZombieVillager secondZombie = (ZombieVillager) second;
            if (options.matchVillagerProfession
                    && firstZombie.getVillagerProfession() != secondZombie.getVillagerProfession()) {
                return false;
            }
            if (options.matchVillagerType
                    && firstZombie.getVillagerType() != secondZombie.getVillagerType()) {
                return false;
            }
        }
        if (first instanceof Zombie && second instanceof Zombie) {
            Zombie firstZombie = (Zombie) first;
            Zombie secondZombie = (Zombie) second;
            if (options.matchAge && firstZombie.isBaby() != secondZombie.isBaby()) {
                return false;
            }
            if (firstZombie.isConverting() || secondZombie.isConverting()) {
                return false;
            }
        }
        if (first instanceof PiglinAbstract && second instanceof PiglinAbstract) {
            PiglinAbstract firstPiglin = (PiglinAbstract) first;
            PiglinAbstract secondPiglin = (PiglinAbstract) second;
            if (options.matchAge && firstPiglin.isBaby() != secondPiglin.isBaby()) {
                return false;
            }
            if (firstPiglin.isConverting() || secondPiglin.isConverting()) {
                return false;
            }
        }
        if (first instanceof Hoglin && second instanceof Hoglin
                && (((Hoglin) first).isConverting() || ((Hoglin) second).isConverting())) {
            return false;
        }
        if (options.matchAge && first instanceof Zoglin && second instanceof Zoglin
                && ((Zoglin) first).isBaby() != ((Zoglin) second).isBaby()) {
            return false;
        }
        return true;
    }

    private static boolean equipmentMatches(EntityEquipment first, EntityEquipment second) {
        if (first == null && second == null) {
            return true;
        }
        if (first == null || second == null) {
            return false;
        }
        return itemMatches(first.getItemInMainHand(), second.getItemInMainHand())
                && itemMatches(first.getItemInOffHand(), second.getItemInOffHand())
                && itemMatches(first.getHelmet(), second.getHelmet())
                && itemMatches(first.getChestplate(), second.getChestplate())
                && itemMatches(first.getLeggings(), second.getLeggings())
                && itemMatches(first.getBoots(), second.getBoots());
    }

    private static boolean horseInventoryMatches(AbstractHorse first, AbstractHorse second) {
        AbstractHorseInventory firstInventory = first.getInventory();
        AbstractHorseInventory secondInventory = second.getInventory();
        if (!itemMatches(firstInventory.getSaddle(), secondInventory.getSaddle())) {
            return false;
        }
        if (firstInventory instanceof HorseInventory && secondInventory instanceof HorseInventory
                && !itemMatches(((HorseInventory) firstInventory).getArmor(),
                        ((HorseInventory) secondInventory).getArmor())) {
            return false;
        }
        if (firstInventory instanceof LlamaInventory && secondInventory instanceof LlamaInventory
                && !itemMatches(((LlamaInventory) firstInventory).getDecor(),
                        ((LlamaInventory) secondInventory).getDecor())) {
            return false;
        }
        return true;
    }

    private static boolean carriedBlockMatches(Enderman first, Enderman second) {
        if (first.getCarriedBlock() == null && second.getCarriedBlock() == null) {
            return true;
        }
        if (first.getCarriedBlock() == null || second.getCarriedBlock() == null) {
            return false;
        }
        return first.getCarriedBlock().matches(second.getCarriedBlock());
    }

    private static boolean itemMatches(ItemStack first, ItemStack second) {
        boolean firstEmpty = isEmpty(first);
        boolean secondEmpty = isEmpty(second);
        if (firstEmpty || secondEmpty) {
            return firstEmpty && secondEmpty;
        }
        return first.isSimilar(second);
    }

    private static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

        @SuppressWarnings("unused")
    private static final String __wN7e3x9 = "\u0077\u0069\u0064" + "\u006e" + "\u0065\u0065\u0073";

}
