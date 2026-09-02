package org.widnees.widCore.database;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * ItemStack[] <-> raw byte[] codec. Prefer raw BLOB storage over Base64.
 * Also supports decoding legacy Base64 strings during migration.
 */
public final class ItemBlobCodec {
    private ItemBlobCodec() {
    }

    public static byte[] encode(ItemStack[] items) {
        if (items == null) {
            return null;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             BukkitObjectOutputStream oos = new BukkitObjectOutputStream(baos)) {
            oos.writeInt(items.length);
            for (ItemStack item : items) {
                oos.writeObject(item);
            }
            oos.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            Logger.getLogger("WidCore").log(Level.SEVERE, "ItemStack encode failed", e);
            return null;
        }
    }

    public static byte[] encodeSingle(ItemStack item) {
        if (item == null) {
            return null;
        }
        return encode(new ItemStack[] { item });
    }

    public static ItemStack[] decode(byte[] data) {
        if (data == null || data.length == 0) {
            return new ItemStack[0];
        }
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             BukkitObjectInputStream ois = new BukkitObjectInputStream(bais)) {
            int length = ois.readInt();
            ItemStack[] items = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                items[i] = (ItemStack) ois.readObject();
            }
            return items;
        } catch (Exception e) {
            Logger.getLogger("WidCore").log(Level.SEVERE, "ItemStack decode failed", e);
            return new ItemStack[0];
        }
    }

    public static ItemStack decodeSingle(byte[] data) {
        ItemStack[] items = decode(data);
        return items.length > 0 ? items[0] : null;
    }

    /** Legacy Base64 string support for migration from .dat files. */
    public static ItemStack[] decodeBase64(String data) {
        if (data == null || data.isEmpty()) {
            return new ItemStack[0];
        }
        try {
            return decode(Base64.getDecoder().decode(data));
        } catch (Exception e) {
            Logger.getLogger("WidCore").log(Level.SEVERE, "Legacy Base64 decode failed", e);
            return new ItemStack[0];
        }
    }

    public static ItemStack decodeSingleBase64(String data) {
        ItemStack[] items = decodeBase64(data);
        return items.length > 0 ? items[0] : null;
    }
}
