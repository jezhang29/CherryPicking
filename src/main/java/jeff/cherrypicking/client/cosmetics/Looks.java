package jeff.cherrypicking.client.cosmetics;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;

import jeff.cherrypicking.CherryPicking;
import jeff.cherrypicking.client.dungeon.DungeonState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.equipment.trim.ArmorTrim;
import net.minecraft.world.item.equipment.trim.TrimMaterial;
import net.minecraft.world.item.equipment.trim.TrimPattern;

/**
 * Gives a friend's armor its look, for the render-state mixins.
 *
 * <p>Vanilla reads every armor look from the stack's components. So the look is a copy of the stack
 * with those components set, swapped in when the game builds the friend's render state, and vanilla
 * draws the copy as it is. The world's stack is never changed.
 *
 * <p>Render thread only. The caches are plain maps for that reason.
 */
public final class Looks {
	private record Key(UUID player, EquipmentSlot slot) {
	}

	/**
	 * The copy made for {@code source} from {@code payload}, and the look it has. With no look,
	 * {@code result} is {@code source} itself and {@code look} is empty.
	 */
	private record Styled(ItemStack source, Payload payload, ItemStack result, Optional<Cosmetic> look) {
	}

	private static final Map<Key, Styled> STYLED = new HashMap<>();
	private static final Map<String, ResolvableProfile> PROFILES = new HashMap<>();
	private static final Set<Cosmetic.Trim> MISSING_TRIMS = new HashSet<>();

	private Looks() {
	}

	/**
	 * The stack to draw in {@code slot}. It is {@code stack} unless the wearer is a friend with a look
	 * for it. Called several times per living entity per frame, so every other entity leaves at the
	 * first checks.
	 */
	public static ItemStack styled(LivingEntity entity, EquipmentSlot slot, ItemStack stack) {
		if (!Cosmetics.enabled() || FriendLooks.isEmpty() || stack.isEmpty() || !(entity instanceof Player)
				|| !DungeonState.inSkyBlock()) {
			return stack;
		}
		Payload payload = FriendLooks.of(entity.getUUID());
		if (payload == null) {
			return stack;
		}

		// The same stack object stays in the slot until the server sends a change, so a copy is made
		// once per change, not once per frame.
		Key key = new Key(entity.getUUID(), slot);
		Styled styled = STYLED.get(key);
		if (styled == null || styled.source() != stack || styled.payload() != payload) {
			Optional<Cosmetic> look = payload.look(slot, SkyblockItem.id(SkyblockItem.tag(stack)));
			styled = new Styled(stack, payload, look.map(found -> apply(found, stack)).orElse(stack), look);
			STYLED.put(key, styled);
		}
		animate(styled);
		return styled.result();
	}

	/**
	 * Moves the copy's animations to now. The render state is built from the copy in the same frame,
	 * so changing the cached copy is safe.
	 */
	private static void animate(Styled styled) {
		styled.look().flatMap(Cosmetic::animatedDye).ifPresent(dye -> styled.result().set(DataComponents.DYED_COLOR,
				new DyedItemColor(AnimatedDyes.color(dye, Util.getMillis() / 1000.0))));
	}

	private static ItemStack apply(Cosmetic look, ItemStack stack) {
		ItemStack copy = stack.copy();
		// Skyblocker draws its own looks on any stack with a uuid in its config. A friend's armor has
		// no uuid; your own does. Without the uuid, the look here wins on your own armor too, which
		// is what the self-test needs, and a friend's armor is unchanged.
		CompoundTag tag = SkyblockItem.tag(stack);
		if (tag.contains("uuid")) {
			tag.remove("uuid");
			copy.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		}
		look.dye().ifPresent(rgb -> copy.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb)));
		look.glint().ifPresent(glint -> copy.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, glint));
		look.trim().flatMap(Looks::armorTrim).ifPresent(trim -> copy.set(DataComponents.TRIM, trim));
		if (copy.is(Items.PLAYER_HEAD)) {
			look.helmetTexture().map(Looks::profile).ifPresent(profile -> copy.set(DataComponents.PROFILE, profile));
		}
		return copy;
	}

	/** Empty, logged once per trim, if this client's registries lack the material or the pattern. */
	private static Optional<ArmorTrim> armorTrim(Cosmetic.Trim trim) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return Optional.empty();
		}
		Optional<Holder.Reference<TrimMaterial>> material = level.registryAccess().lookup(Registries.TRIM_MATERIAL)
				.flatMap(registry -> registry.get(ResourceKey.create(Registries.TRIM_MATERIAL, trim.material())));
		Optional<Holder.Reference<TrimPattern>> pattern = level.registryAccess().lookup(Registries.TRIM_PATTERN)
				.flatMap(registry -> registry.get(ResourceKey.create(Registries.TRIM_PATTERN, trim.pattern())));
		if (material.isEmpty() || pattern.isEmpty()) {
			if (MISSING_TRIMS.add(trim)) {
				CherryPicking.LOGGER.warn("Friend looks: this client has no trim {} / {}; left out.",
						trim.material(), trim.pattern());
			}
			return Optional.empty();
		}
		return Optional.of(new ArmorTrim(material.get(), pattern.get()));
	}

	/**
	 * A resolved profile that carries only the texture, so no lookup goes to Mojang. Ported from
	 * Skyblocker's {@code CustomHelmetTextures.getProfile} (LGPL-3.0). Cached, because a new profile
	 * object makes the skin load again.
	 */
	private static ResolvableProfile profile(String texture) {
		return PROFILES.computeIfAbsent(texture, value -> ResolvableProfile.createResolved(new GameProfile(
				UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)), "custom",
				new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", value))))));
	}
}
