package com.magusgeek.brutaltester;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class ArenaStats {
	private final Map<ArenaPlayer, int[]> stats;
	private final List<ArenaPlayer> arenaPlayers;

	public ArenaStats(List<ArenaPlayer> arenaPlayers) {
		this.arenaPlayers = arenaPlayers;
		this.stats = new HashMap<ArenaPlayer, int[]>();
		for (var player : arenaPlayers) {
			this.stats.put(player, new int[3]);
		}
	}

	public void finish(ArenaPlayer player, Score score) {
		stats.get(player)[score.ordinal()] += 1;
	}

	public void add(int[] scores) {
		for (int i = 0; i < arenaPlayers.size(); ++i) {
			for (int j = i + 1; j < arenaPlayers.size(); ++j) {
				if (scores[i] > scores[j]) {
					finish(arenaPlayers.get(i), Score.VICTORY);
					finish(arenaPlayers.get(j), Score.DEFEAT);
				} else if (scores[i] < scores[j]) {
					finish(arenaPlayers.get(i), Score.DEFEAT);
					finish(arenaPlayers.get(j), Score.VICTORY);
				} else {
					finish(arenaPlayers.get(i), Score.DRAW);
					finish(arenaPlayers.get(j), Score.DRAW);
				}
			}
		}
	}

	public static ArenaStats merge(ArenaStats a, ArenaStats b) {
		var players = new HashSet<ArenaPlayer>();
		players.addAll(a.arenaPlayers);
		players.addAll(b.arenaPlayers);
		var result = new ArenaStats(players.stream().toList());
		var zeroScore = new int[3];
		for (var player : players) {
			for (int t = 0; t < 3; t++) {
				result.stats.get(player)[t] += a.stats.getOrDefault(player, zeroScore)[t];
				result.stats.get(player)[t] += b.stats.getOrDefault(player, zeroScore)[t];
			}
		}

		return result;
	}

	public void print() {
		var lines = arenaPlayers.stream().sorted((a, b) -> {
			var statsA = stats.get(a);
			var scoreA = (float) statsA[0] / (statsA[0] + statsA[1] + statsA[2]);
			var statsB = stats.get(b);
			var scoreB = (float) statsB[0] / (statsB[0] + statsB[1] + statsB[2]);
			return Float.compare(scoreB, scoreA);
		}).map(player -> {
			var playerStats = stats.get(player);
			int nbGames = playerStats[0] + playerStats[1] + playerStats[2];
			return "%s : %s | %s | %s".formatted(player.playerName(), percent(playerStats[0], nbGames),
					percent(playerStats[1], nbGames), percent(playerStats[2], nbGames));
		}).toList();
		for (int i = 0; i < lines.size(); i++) {
			System.out.println("%s. %s".formatted(i + 1, lines.get(i)));
		}
	}

	private String percent(float amount, int total) {
		return String.format("%.2f", amount * 100.0 / total) + "%";
	}

	public enum Score {
		VICTORY, DEFEAT, DRAW;
	}
}
