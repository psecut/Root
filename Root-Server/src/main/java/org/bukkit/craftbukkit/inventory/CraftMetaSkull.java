package org.bukkit.craftbukkit.inventory;

import java.util.Map;

// PaperSpigot start
// PaperSpigot end

import org.bukkit.Material;
import org.bukkit.configuration.serialization.DelegateDeserialization;
import org.bukkit.craftbukkit.inventory.CraftMetaItem.SerializableMeta;
import org.bukkit.inventory.meta.SkullMeta;

import com.google.common.collect.ImmutableMap.Builder;
import com.mojang.authlib.GameProfile;

import net.minecraft.server.EntityPlayer;
import net.minecraft.server.GameProfileSerializer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.NBTBase;
import net.minecraft.server.NBTTagCompound;
import net.minecraft.server.TileEntitySkull;

@DelegateDeserialization(SerializableMeta.class)
class CraftMetaSkull extends CraftMetaItem implements SkullMeta {

	@ItemMetaKey.Specific(ItemMetaKey.Specific.To.NBT)
	static final ItemMetaKey SKULL_PROFILE = new ItemMetaKey("SkullProfile");

	static final ItemMetaKey SKULL_OWNER = new ItemMetaKey("SkullOwner", "skull-owner");
	static final int MAX_OWNER_LENGTH = 16;

	private GameProfile profile;

	CraftMetaSkull(CraftMetaItem meta) {
		super(meta);
		if (!(meta instanceof CraftMetaSkull)) {
			return;
		}
		CraftMetaSkull skullMeta = (CraftMetaSkull) meta;
		this.profile = skullMeta.profile;
	}

	CraftMetaSkull(NBTTagCompound tag) {
		super(tag);

		if (tag.hasKeyOfType(SKULL_OWNER.NBT, 10)) {
			profile = GameProfileSerializer.deserialize(tag.getCompound(SKULL_OWNER.NBT));
		} else if (tag.hasKeyOfType(SKULL_OWNER.NBT, 8) && !tag.getString(SKULL_OWNER.NBT).isEmpty()) {
			profile = new GameProfile(null, tag.getString(SKULL_OWNER.NBT));
		}
	}

	CraftMetaSkull(Map<String, Object> map) {
		super(map);
		if (profile == null) {
			setOwner(SerializableMeta.getString(map, SKULL_OWNER.BUKKIT, true));
		}
	}

	@Override
	void deserializeInternal(NBTTagCompound tag) {
		if (tag.hasKeyOfType(SKULL_PROFILE.NBT, 10)) {
			profile = GameProfileSerializer.deserialize(tag.getCompound(SKULL_PROFILE.NBT));
		}
	}

	@Override
	void serializeInternal(final Map<String, NBTBase> internalTags) {
		if (profile != null) {
			NBTTagCompound nbtData = new NBTTagCompound();
			GameProfileSerializer.serialize(nbtData, profile);
			internalTags.put(SKULL_PROFILE.NBT, nbtData);
		}
	}

	@Override
	void applyToItem(final NBTTagCompound tag) { // Spigot - make final
		super.applyToItem(tag);

		if (profile != null) {
			NBTTagCompound owner = new NBTTagCompound();
			GameProfileSerializer.serialize(owner, profile);
			tag.set(SKULL_OWNER.NBT, owner);
			// Spigot start - do an async lookup of the profile.
			// Unfortunately there is not way to refresh the holding
			// inventory, so that responsibility is left to the user.
			net.minecraft.server.TileEntitySkull.b(profile, input -> {
				NBTTagCompound owner1 = new NBTTagCompound();
				GameProfileSerializer.serialize(owner1, input);
				tag.set(SKULL_OWNER.NBT, owner1);
				return false;
			});
			// Spigot end
		}
	}

	@Override
	boolean isEmpty() {
		return super.isEmpty() && isSkullEmpty();
	}

	boolean isSkullEmpty() {
		return profile == null;
	}

	@Override
	boolean applicableTo(Material type) {
		switch (type) {
		case SKULL_ITEM:
			return true;
		default:
			return false;
		}
	}

	@Override
	public CraftMetaSkull clone() {
		return (CraftMetaSkull) super.clone();
	}

	@Override
	public boolean hasOwner() {
		return profile != null && profile.getName() != null;
	}

	@Override
	public String getOwner() {
		return hasOwner() ? profile.getName() : null;
	}

	@Override
	public boolean setOwner(String name) {
		// Feather - Null name would break everything and makes no sense and causes NPE
		if (name == null || name.length() > MAX_OWNER_LENGTH) {
			return false;
		}

		// PaperSpigot start - Check usercache if the player is online
		EntityPlayer player = MinecraftServer.getServer().getPlayerList().getPlayer(name);
		if (profile == null && player != null) {
			profile = player.getProfile();
			// PaperSpigot end
		}

		if (profile == null) {
			// name.toLowerCase(java.util.Locale.ROOT) causes the NPE
			profile = TileEntitySkull.skinCache.getIfPresent(name.toLowerCase(java.util.Locale.ROOT)); // Paper // tries
																										// to get from
																										// skincache
		}
		if (profile == null) {
			profile = new GameProfile(null, name);
		}

		return true;
	}

	@Override
	int applyHash() {
		final int original;
		int hash = original = super.applyHash();
		if (hasOwner()) {
			hash = 61 * hash + profile.hashCode();
		}
		return original != hash ? CraftMetaSkull.class.hashCode() ^ hash : hash;
	}

	@Override
	boolean equalsCommon(CraftMetaItem meta) {
		if (!super.equalsCommon(meta)) {
			return false;
		}
		if (meta instanceof CraftMetaSkull) {
			CraftMetaSkull that = (CraftMetaSkull) meta;

			return (this.hasOwner() ? that.hasOwner() && profile.equals(that.profile) : !that.hasOwner());
		}
		return true;
	}

	@Override
	boolean notUncommon(CraftMetaItem meta) {
		return super.notUncommon(meta) && (meta instanceof CraftMetaSkull || isSkullEmpty());
	}

	@Override
	Builder<String, Object> serialize(Builder<String, Object> builder) {
		super.serialize(builder);
		if (hasOwner()) {
			return builder.put(SKULL_OWNER.BUKKIT, profile.getName());
		}
		return builder;
	}
}
