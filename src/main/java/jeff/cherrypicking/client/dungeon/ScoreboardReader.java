package jeff.cherrypicking.client.dungeon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Reads the SkyBlock sidebar. Hypixel writes every sidebar line as a team
 * prefix/suffix around a throwaway score holder, so the visible line is
 * prefix + holder + suffix - the same thing vanilla renders.
 *
 * <p>Copied from coalroutegenerator's {@code location.ScoreboardReader}.
 */
public final class ScoreboardReader {
	private static final char SECTION_SIGN = (char) 0xA7;
	private static final char DELETE = (char) 0x7F;

	private ScoreboardReader() {
	}

	public static String title(ClientLevel level) {
		Objective objective = sidebar(level);
		return objective == null ? "" : strip(objective.getDisplayName().getString());
	}

	/** Sidebar lines, top first, colour codes removed. */
	public static List<String> lines(ClientLevel level) {
		Objective objective = sidebar(level);
		if (objective == null) {
			return List.of();
		}

		Scoreboard scoreboard = level.getScoreboard();
		List<PlayerScoreEntry> entries = new ArrayList<>(scoreboard.listPlayerScores(objective));
		entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());

		List<String> lines = new ArrayList<>(entries.size());
		for (PlayerScoreEntry entry : entries) {
			if (entry.isHidden()) {
				continue;
			}

			PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
			Component full = team == null
					? entry.ownerName()
					: PlayerTeam.formatNameForTeam(team, entry.ownerName());
			String line = strip(full.getString());
			if (!line.isBlank()) {
				lines.add(line);
			}
		}
		return lines;
	}

	private static Objective sidebar(ClientLevel level) {
		return level == null ? null : level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
	}

	/** Drops section-sign colour codes and control characters. */
	public static String strip(String text) {
		StringBuilder out = new StringBuilder(text.length());
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == SECTION_SIGN && i + 1 < text.length()) {
				i++;
			} else if (c >= ' ' && c != DELETE) {
				out.append(c);
			}
		}
		return out.toString().trim();
	}
}
