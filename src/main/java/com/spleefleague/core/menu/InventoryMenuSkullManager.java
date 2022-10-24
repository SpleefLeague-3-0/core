package com.spleefleague.core.menu;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mongodb.client.MongoCollection;
import com.spleefleague.core.Core;
import com.spleefleague.core.logger.CoreLogger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.bson.Document;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.craftbukkit.v1_19_R1.inventory.CraftItemStack;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class InventoryMenuSkullManager {

    public static class Texture {

        public String value, signature;

        public Texture(String value, String signature) {
            this.value = value;
            this.signature = signature;
        }

    }

    private static final Map<UUID, Texture> uuidTextureMap;
    private static final Set<UUID> loadingSkulls;

    private static MongoCollection<Document> skullCollection;

    public static void init() {
        skullCollection = Core.getInstance().getPluginDB().getCollection("Skulls");
    }

    static {
        uuidTextureMap = new HashMap<>();
        loadingSkulls = new HashSet<>();
    }

    private static void loadTexture(UUID uuid) {
        Document baseDoc = new Document("identifier", uuid.toString());
        Document doc = skullCollection.find(baseDoc).first();
        Texture texture;
        if (doc != null) {
            texture = new Texture(doc.getString("value"), doc.getString("signature"));
        } else {
            try {
                InputStreamReader reader = new InputStreamReader(new URL("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString() + "?unsigned=false").openStream());
                JsonObject textureProperty = new JsonParser().parse(reader).getAsJsonObject().get("properties").getAsJsonArray().get(0).getAsJsonObject();

                String value = textureProperty.get("value").getAsString();
                String signature = textureProperty.get("signature").getAsString();
                texture = new Texture(value, signature);

                baseDoc.append("value", value).append("signature", signature);
                skullCollection.insertOne(baseDoc);
            } catch (IOException exception) {
                CoreLogger.logError(exception);
                return;
            }
        }
        uuidTextureMap.put(uuid, texture);
    }

    public static Texture getTexture(UUID uuid) {
        if (!uuidTextureMap.containsKey(uuid)) {
            loadTexture(uuid);
        }
        return uuidTextureMap.get(uuid);
    }

    public static ItemStack getDefaultSkull() {
        return new ItemStack(Material.PLAYER_HEAD);
    }

    private static void loadSkull(UUID uuid) {
        loadingSkulls.add(uuid);
        Bukkit.getScheduler().runTaskAsynchronously(Core.getInstance(), () -> {
            loadTexture(uuid);
            loadingSkulls.remove(uuid);
        });
    }

    private static ItemStack getPlayerSkull(UUID uuid, Texture texture) {
        net.minecraft.world.item.ItemStack nmsStack = CraftItemStack.asNMSCopy(new ItemStack(Material.PLAYER_HEAD));

        CompoundTag compound = nmsStack.getTag();
        if (compound == null) {
            nmsStack.setTag(new CompoundTag());
            compound = nmsStack.getTag();
        }
        if (compound == null) {
            return CraftItemStack.asBukkitCopy(nmsStack);
        }

        CompoundTag skullOwner = new CompoundTag();
        skullOwner.putString("Id", uuid.toString());

        CompoundTag properties = new CompoundTag();
        ListTag textures = new ListTag();
        CompoundTag value = new CompoundTag();
        value.putString("Value", texture.value);
        value.putString("Signature", texture.signature);
        textures.add(value);
        properties.put("textures", textures);
        skullOwner.put("Properties", properties);

        compound.put("SkullOwner", skullOwner);
        nmsStack.setTag(compound);

        return CraftItemStack.asBukkitCopy(nmsStack);
    }

    public static ItemStack getPlayerSkullForced(UUID uuid) {
        return getPlayerSkull(uuid, getTexture(uuid));
    }

    public static ItemStack getPlayerSkull(UUID uuid) {
        return getPlayerSkullForced(uuid);

        /*
        CorePlayer cp = Core.getInstance().getPlayers().getOffline(uuid);
        if (cp != null && cp.getDisguise() != null) {
            uuid = cp.getDisguise();
        }
        Texture texture = uuidTextureMap.get(uuid);
        if (texture == null) {
            loadSkull(uuid);
            return new ItemStack(Material.PLAYER_HEAD);
        }

        return getPlayerSkull(uuid, texture);
         */
    }

}
