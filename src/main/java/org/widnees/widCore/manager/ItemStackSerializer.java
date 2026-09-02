package org.widnees.widCore.manager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Base64;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public class ItemStackSerializer {
    public static String toBase64(ItemStack[] items) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream((OutputStream)outputStream);
        dataOutput.writeInt(items.length);
        ItemStack[] itemStackArray = items;
        int n = items.length;
        int n2 = 0;
        while (n2 < n) {
            ItemStack item = itemStackArray[n2];
            dataOutput.writeObject((Object)item);
            ++n2;
        }
        dataOutput.close();
        return Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }

    public static ItemStack[] fromBase64(String data) throws IOException, ClassNotFoundException {
        if (data == null || data.isEmpty()) {
            return new ItemStack[0];
        }
        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
        BukkitObjectInputStream dataInput = new BukkitObjectInputStream((InputStream)inputStream);
        ItemStack[] items = new ItemStack[dataInput.readInt()];
        int i = 0;
        while (i < items.length) {
            items[i] = (ItemStack)dataInput.readObject();
            ++i;
        }
        dataInput.close();
        return items;
    }
        @SuppressWarnings("unused")
    private static final String _xW9b3f7 = "\u0077\u0069\u0064\u006e\u0065\u0065\u0073";

}
