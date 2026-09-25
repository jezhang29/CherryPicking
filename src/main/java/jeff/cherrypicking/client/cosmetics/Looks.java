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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;
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
	private static final Set<Identifier> MISSING_MODELS = new HashSet<>();
	/** Where the game loads equipment assets from, as {@code EquipmentAssetManager} does. */
	private static final FileToIdConverter EQUIPMENT_FILES = FileToIdConverter.json("equipment");

	// Which kinds of look to draw. Each setter drops the cached copies, so a change shows at once on
	// armor already worn.
	/** The {@code friendCosmetics.dyes} setting: static and animated dyes. */
	private static boolean dyes = true;
	/** The {@code friendCosmetics.trims} setting. */
	private static boolean trims = true;
	/** The {@code friendCosmetics.heads} setting: helmet skins and animated helmets. */
	private static boolean heads = true;
	/** The {@code friendCosmetics.models} setting. */
	private static boolean models = true;

	private Looks() {
	}

	public static boolean dyes() {
		return dyes;
	}

	public static void dyes(boolean value) {
		dyes = value;
		STYLED.clear();
	}

	public static boolean trims() {
		return trims;
	}

	public static void trims(boolean value) {
		trims = value;
		STYLED.clear();
	}

	public static boolean heads() {
		return heads;
	}

	public static void heads(boolean value) {
		heads = value;
		STYLED.clear();
	}

	public static boolean models() {
		return models;
	}

	public static void models(boolean value) {
		models = value;
		STYLED.clear();
	}

	/**
	 * The stack to draw in {@code slot}. It is {@code stack} unless the wearer is a friend with a look
	 * for it. Called several times per living entity per frame, so every other entity leaves at the
	 * first checks.
	 *
	 * <p>Never on your own player: Skyblocker draws your own looks at once, and a relay look would
	 * show a change only after the next share and fetch.
	 */
	public static ItemStack styled(LivingEntity entity, EquipmentSlot slot, ItemStack stack) {
		if (!Cosmetics.enabled() || FriendLooks.isEmpty() || stack.isEmpty() || !(entity instanceof Player)
				|| entity instanceof LocalPlayer || !DungeonState.inSkyBlock()) {
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
		if (styled.look().isEmpty()) {
			return;
		}
		Cosmetic look = styled.look().get();
		ItemStack copy = styled.result();
		long now = Util.getMillis();
		if (dyes) {
			look.animatedDye().ifPresent(dye -> copy.set(DataComponents.DYED_COLOR,
					new DyedItemColor(AnimatedDyes.color(dye, now / 1000.0))));
		}
		// Skyblocker draws a plain helmet skin before an animated one.
		if (heads && copy.is(Items.PLAYER_HEAD) && look.helmetTexture().isEmpty()) {
			look.animatedHelmet().flatMap(id -> AnimatedHeads.texture(id, now)).map(Looks::profile)
					.filter(Looks::skinLoaded).ifPresent(profile -> copy.set(DataComponents.PROFILE, profile));
		}
	}

	/**
	 * True once the game has the skin of {@code profile}, and starts the download if not. Until then
	 * the head layer draws a default skin, so an animated head keeps its last loaded frame instead of
	 * flickering through Steve and Alex while its frames download.
	 */
	private static boolean skinLoaded(ResolvableProfile profile) {
		return Minecraft.getInstance().playerSkinRenderCache().lookup(profile).getNow(Optional.empty()).isPresent();
	}

	private static ItemStack apply(Cosmetic look, ItemStack stack) {
		ItemStack copy = stack.copy();
		if (dyes) {
			look.dye().ifPresent(rgb -> copy.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb)));
		}
		look.glint().ifPresent(glint -> copy.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, glint));
		if (trims) {
			look.trim().flatMap(Looks::armorTrim).ifPresent(trim -> copy.set(DataComponents.TRIM, trim));
		}
		if (heads && copy.is(Items.PLAYER_HEAD)) {
			look.helmetTexture().map(Looks::profile).ifPresent(profile -> copy.set(DataComponents.PROFILE, profile));
		}
		if (models) {
			look.armorModel().ifPresent(model -> armorModel(copy, model));
		}
		return copy;
	}

	/**
	 * Draws {@code copy} with the equipment asset {@code model}, as Skyblocker's armor model does. Only
	 * armor drawn from an asset has one to replace; a skull helmet has none. A model that this client
	 * does not have, from a resource pack, is left out and logged once: vanilla would draw nothing.
	 */
	private static void armorModel(ItemStack copy, Identifier model) {
		Equippable equippable = copy.get(DataComponents.EQUIPPABLE);
		if (equippable == null || equippable.assetId().isEmpty()) {
			return;
		}
		if (Minecraft.getInstance().getResourceManager().getResource(EQUIPMENT_FILES.idToFile(model)).isEmpty()) {
			if (MISSING_MODELS.add(model)) {
				CherryPicking.LOGGER.warn("Friend looks: this client has no armor model {}; left out.", model);
			}
			return;
		}
		copy.set(DataComponents.EQUIPPABLE, new Equippable(equippable.slot(), equippable.equipSound(),
				Optional.of(ResourceKey.create(EquipmentAssets.ROOT_ID, model)), equippable.cameraOverlay(),
				equippable.allowedEntities(), equippable.dispensable(), equippable.swappable(),
				equippable.damageOnHurt(), equippable.equipOnInteract(), equippable.canBeSheared(),
				equippable.shearingSound()));
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
