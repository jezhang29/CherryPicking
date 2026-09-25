package jeff.cherrypicking.client.cosmetics;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import jeff.cherrypicking.CherryPicking;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;

/**
 * One player's shared looks: the document in section 6 of docs/friend-cosmetics-plan.md.
 *
 * <p>A payload comes from another player's client, so {@link #decode} is the edge: it applies every
 * check in section 9 of that plan. A look or field that fails a check is dropped, and the count is
 * logged. Everything past {@code decode} trusts the values. {@link #write} makes the document this
 * client publishes.
 *
 * @param looks    item uuid to look
 * @param equipped the item uuid worn in each armor slot
 */
record Payload(Map<String, Cosmetic> looks, Map<EquipmentSlot, String> equipped) {
	static final int FORMAT = 1;
	static final int MAX_CHARS = 64 * 1024;
	static final int MAX_LOOKS = 500;
	static final int MAX_KEYFRAMES = 32;

	private static final Pattern ITEM_UUID = Pattern.compile("[0-9a-f-]{36}");
	private static final Pattern SKYBLOCK_ID = Pattern.compile("[A-Z0-9_:;-]{1,64}");
	private static final List<EquipmentSlot> ARMOR =
			List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);

	/**
	 * The look for the armor worn in {@code slot}, whose SkyBlock id is {@code wornId}:
	 *
	 * <ol>
	 *   <li>the look of the item published as worn in that slot, if its id matches;</li>
	 *   <li>otherwise the only look with that id, which covers a wardrobe swap before the new
	 *       {@code equipped} arrives;</li>
	 *   <li>otherwise none, so old data never puts a look on the wrong armor.</li>
	 * </ol>
	 */
	Optional<Cosmetic> look(EquipmentSlot slot, String wornId) {
		if (wornId.isEmpty()) {
			return Optional.empty();
		}
		String wornUuid = equipped.get(slot);
		Cosmetic worn = wornUuid == null ? null : looks.get(wornUuid);
		if (worn != null && worn.id().equals(wornId)) {
			return Optional.of(worn);
		}
		List<Cosmetic> sameId = looks.values().stream()
				.filter(look -> look.id().equals(wornId))
				.limit(2)
				.toList();
		return sameId.size() == 1 ? Optional.of(sameId.getFirst()) : Optional.empty();
	}

	/**
	 * Reads a payload. Empty, with one warning, if the whole document is unusable: too large, not
	 * JSON, or not format 1. {@code source} names where it came from, for the log.
	 */
	static Optional<Payload> decode(String json, String source) {
		if (json.length() > MAX_CHARS) {
			CherryPicking.LOGGER.warn("Friend looks: {} is larger than {} characters; ignored.", source, MAX_CHARS);
			return Optional.empty();
		}
		JsonObject root;
		try {
			JsonElement parsed = JsonParser.parseString(json);
			if (!parsed.isJsonObject()) {
				throw new JsonParseException("not a JSON object");
			}
			root = parsed.getAsJsonObject();
		} catch (JsonParseException failed) {
			CherryPicking.LOGGER.warn("Friend looks: {} is not valid JSON; ignored. {}", source, failed.getMessage());
			return Optional.empty();
		}
		if (!(root.get("format") instanceof JsonPrimitive format) || !format.isNumber()
				|| format.getAsDouble() != FORMAT) {
			CherryPicking.LOGGER.warn("Friend looks: {} is not format {}; ignored.", source, FORMAT);
			return Optional.empty();
		}

		Map<String, Cosmetic> looks = new HashMap<>();
		int dropped = 0;
		int droppedFields = 0;
		if (root.get("looks") instanceof JsonObject entries) {
			for (Map.Entry<String, JsonElement> entry : entries.entrySet()) {
				Optional<Cosmetic> look = looks.size() < MAX_LOOKS && ITEM_UUID.matcher(entry.getKey()).matches()
						&& entry.getValue() instanceof JsonObject fields ? cosmetic(fields) : Optional.empty();
				if (look.isPresent()) {
					looks.put(entry.getKey(), look.get());
					droppedFields += given(entry.getValue().getAsJsonObject()) - kept(look.get());
				} else {
					dropped++;
				}
			}
		}

		Map<EquipmentSlot, String> equipped = new EnumMap<>(EquipmentSlot.class);
		if (root.get("equipped") instanceof JsonObject slots) {
			for (EquipmentSlot slot : ARMOR) {
				if (slots.get(slot.getName()) instanceof JsonPrimitive uuid && uuid.isString()
						&& ITEM_UUID.matcher(uuid.getAsString()).matches()) {
					equipped.put(slot, uuid.getAsString());
				}
			}
		}

		if (dropped > 0 || droppedFields > 0) {
			CherryPicking.LOGGER.warn("Friend looks: dropped {} bad looks and {} bad fields from {}.", dropped,
					droppedFields, source);
		}
		return Optional.of(new Payload(Map.copyOf(looks), Map.copyOf(equipped)));
	}

	/**
	 * The document to publish.
	 *
	 * @param looks    item uuid to look fields, as {@link SkyblockerFile#looks} gives them
	 * @param ids      item uuid to SkyBlock id, for the items this client has seen
	 * @param equipped the uuid worn in each armor slot
	 * @return JSON. A look with no known id is left out, because no other client could match it.
	 *         If the document is too large, only the worn looks are kept, with a warning.
	 */
	static String write(Map<String, JsonObject> looks, Map<String, String> ids, Map<EquipmentSlot, String> equipped) {
		String json = document(looks, ids, equipped, uuid -> true);
		if (json.length() > MAX_CHARS) {
			CherryPicking.LOGGER.warn("Friend looks: your looks are larger than {} characters; sharing only the worn ones.",
					MAX_CHARS);
			json = document(looks, ids, equipped, equipped::containsValue);
		}
		return json;
	}

	private static String document(Map<String, JsonObject> looks, Map<String, String> ids,
			Map<EquipmentSlot, String> equipped, Predicate<String> keep) {
		JsonObject out = new JsonObject();
		out.addProperty("format", FORMAT);
		JsonObject lookOut = new JsonObject();
		looks.forEach((uuid, fields) -> {
			String id = ids.get(uuid);
			if (id != null && keep.test(uuid) && lookOut.size() < MAX_LOOKS) {
				JsonObject look = new JsonObject();
				look.addProperty("id", id);
				fields.entrySet().forEach(field -> look.add(field.getKey(), field.getValue()));
				lookOut.add(uuid, look);
			}
		});
		out.add("looks", lookOut);
		JsonObject worn = new JsonObject();
		for (EquipmentSlot slot : ARMOR) {
			if (equipped.containsKey(slot)) {
				worn.addProperty(slot.getName(), equipped.get(slot));
			}
		}
		out.add("equipped", worn);
		return out.toString();
	}

	private static final List<String> FIELDS = List.of("dye", "animatedDye", "trim", "helmetTexture", "glint");

	private static int given(JsonObject fields) {
		return (int) FIELDS.stream().filter(fields::has).count();
	}

	private static int kept(Cosmetic look) {
		return (look.dye().isPresent() ? 1 : 0) + (look.animatedDye().isPresent() ? 1 : 0)
				+ (look.trim().isPresent() ? 1 : 0) + (look.helmetTexture().isPresent() ? 1 : 0)
				+ (look.glint().isPresent() ? 1 : 0);
	}

	/** One look; empty without a valid id. A field that fails its check is left out. */
	private static Optional<Cosmetic> cosmetic(JsonObject fields) {
		if (!(fields.get("id") instanceof JsonPrimitive id) || !id.isString()
				|| !SKYBLOCK_ID.matcher(id.getAsString()).matches()) {
			return Optional.empty();
		}

		OptionalInt dye = fields.get("dye") instanceof JsonPrimitive rgb && rgb.isNumber()
				? OptionalInt.of(rgb.getAsInt() & 0xFFFFFF) : OptionalInt.empty();

		Optional<Cosmetic.Trim> trim = Optional.empty();
		if (fields.get("trim") instanceof JsonObject parts
				&& parts.get("material") instanceof JsonPrimitive material && material.isString()
				&& parts.get("pattern") instanceof JsonPrimitive pattern && pattern.isString()) {
			Identifier materialId = Identifier.tryParse(material.getAsString());
			Identifier patternId = Identifier.tryParse(pattern.getAsString());
			if (materialId != null && patternId != null) {
				trim = Optional.of(new Cosmetic.Trim(materialId, patternId));
			}
		}

		Optional<String> texture = fields.get("helmetTexture") instanceof JsonPrimitive value && value.isString()
				&& skinOnMojang(value.getAsString()) ? Optional.of(value.getAsString()) : Optional.empty();

		Optional<Boolean> glint = fields.get("glint") instanceof JsonPrimitive value && value.isBoolean()
				? Optional.of(value.getAsBoolean()) : Optional.empty();

		return Optional.of(new Cosmetic(id.getAsString(), dye, animatedDye(fields.get("animatedDye")), trim, texture,
				glint));
	}

	/**
	 * An animated dye with 2 to 32 keyframes in time order, times from 0 to 1, a duration from 0.1 to
	 * 60 s and a delay of 0 or more. Colors keep only their RGB. Skyblocker's own animation needs two
	 * keyframes, and the order is what it blends between.
	 */
	private static Optional<Cosmetic.AnimatedDye> animatedDye(JsonElement value) {
		if (!(value instanceof JsonObject dye) || !(dye.get("keyframes") instanceof JsonArray frames)
				|| frames.size() < 2 || frames.size() > MAX_KEYFRAMES
				|| !(dye.get("cycleBack") instanceof JsonPrimitive cycleBack) || !cycleBack.isBoolean()) {
			return Optional.empty();
		}
		OptionalDouble duration = number(dye.get("duration"));
		OptionalDouble delay = number(dye.get("delay"));
		if (duration.isEmpty() || duration.getAsDouble() < 0.1 || duration.getAsDouble() > 60
				|| delay.isEmpty() || delay.getAsDouble() < 0) {
			return Optional.empty();
		}
		List<Cosmetic.Keyframe> keyframes = new ArrayList<>();
		for (JsonElement frame : frames) {
			if (!(frame instanceof JsonObject keyframe)
					|| !(keyframe.get("color") instanceof JsonPrimitive color) || !color.isNumber()) {
				return Optional.empty();
			}
			OptionalDouble time = number(keyframe.get("time"));
			double previous = keyframes.isEmpty() ? 0 : keyframes.getLast().time();
			if (time.isEmpty() || time.getAsDouble() < previous || time.getAsDouble() > 1) {
				return Optional.empty();
			}
			keyframes.add(new Cosmetic.Keyframe(color.getAsInt() & 0xFFFFFF, (float) time.getAsDouble()));
		}
		return Optional.of(new Cosmetic.AnimatedDye(List.copyOf(keyframes), cycleBack.getAsBoolean(),
				(float) delay.getAsDouble(), (float) duration.getAsDouble()));
	}

	/** A finite JSON number; empty for anything else. */
	private static OptionalDouble number(JsonElement value) {
		if (value instanceof JsonPrimitive number && number.isNumber() && Double.isFinite(number.getAsDouble())) {
			return OptionalDouble.of(number.getAsDouble());
		}
		return OptionalDouble.empty();
	}

	/**
	 * A texture property is base64 JSON whose {@code textures.SKIN.url} the game downloads. Only
	 * Mojang's skin server is allowed, so a friend's data cannot make this client fetch other URLs.
	 */
	static boolean skinOnMojang(String texture) {
		return skinUrl(texture).filter(url -> url.startsWith("http://textures.minecraft.net/")
				|| url.startsWith("https://textures.minecraft.net/")).isPresent();
	}

	/** The skin URL in a texture property; empty if the property is malformed. */
	static Optional<String> skinUrl(String texture) {
		try {
			String json = new String(Base64.getDecoder().decode(texture), StandardCharsets.UTF_8);
			return Optional.of(JsonParser.parseString(json).getAsJsonObject()
					.getAsJsonObject("textures").getAsJsonObject("SKIN").get("url").getAsString());
		} catch (RuntimeException malformed) {
			// Bad base64, bad JSON, a missing key or a key of the wrong type: each throws a
			// different unchecked exception, and each means the same thing here.
			return Optional.empty();
		}
	}
}
