package jeff.cherrypicking.client.dungeon.mob;

/**
 * One starred mob: which entity to box, what it is, and what to call it.
 *
 * <p>Ids, not entities, for the reason coalroutegenerator's {@code Corpse}
 * gives: {@link StarMobWatch} re-reads the live entities every pass, and a hard
 * reference would keep a removed mob alive and make "is it gone" ambiguous.
 *
 * @param standId the name stand's id, or {@code -1} for a hidden Fels found
 *     without one
 * @param mobId the mob's id; the box follows this entity
 * @param name what the label says, e.g. {@code Shadow Assassin}
 */
public record StarMob(int standId, int mobId, MobKind kind, String name) {
}
