package com.magusgeek.brutaltester;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

import com.magusgeek.brutaltester.util.Mutable;
import com.magusgeek.brutaltester.util.SeedGenerator;

public class Main {

	private static final Log LOG = LogFactory.getLog(Main.class);

	private static int t;

	public static void main(String[] args) {
		try {
			Options options = new Options();

			options //
					.addOption("f", true, "Folder containing all java players.") //
					.addOption("h", false, "Print the help") //
					.addOption("v", false, "Verbose mode. Spam incoming.")
					.addOption("n", true, "Number of games to play. Default 1.")
					.addOption("t", true, "Number of thread to spawn for the games. Default 1.")
					.addOption("r", true, "Required. Referee command line.")
					.addOption("l", true, "A directory for games logs") //
					.addOption("s", false, "Swap player positions") //
					.addOption("i", true, "Initial seed. For repeatable tests") //
					.addOption("o", false, "Old mode");

			CommandLine cmd = new DefaultParser().parse(options, args);

			// Need help ?
			if (cmd.hasOption("h") || !cmd.hasOption("r")
					|| (!cmd.hasOption("p1") || !cmd.hasOption("p2")) && !cmd.hasOption("f")) {
				new HelpFormatter().printHelp(
						"-r <referee command line> -p1 <player1 command line> -p2 <player2 command line> -p3 <player3 command line> -p4 <player4 command line> [-o -v -n <games> -t <thread>]",
						options);
				System.exit(0);
			}

			// Verbose mode
			if (cmd.hasOption("v")) {
				Configurator.setRootLevel(Level.ALL);
				LOG.info("Verbose mode activated");
			}

			// Referee command line
			String refereeCmd = cmd.getOptionValue("r");
			LOG.info("Referee command line: " + refereeCmd);

			// Games count
			int n = 1;
			try {
				n = Integer.valueOf(cmd.getOptionValue("n"));
			} catch (Exception exception) {

			}
			LOG.info("Number of games to play: " + n);

			// Thread count
			t = 1;
			try {
				t = Integer.valueOf(cmd.getOptionValue("t"));
			} catch (Exception exception) {

			}
			LOG.info("Number of threads to spawn: " + t);

			// Logs directory
			Path logs = null;
			if (cmd.hasOption("l")) {
				logs = FileSystems.getDefault().getPath(cmd.getOptionValue("l"));
				if (!Files.isDirectory(logs)) {
					throw new NotDirectoryException("Given path for the logs directory is not a directory: " + logs);
				}
			}

			boolean swap = cmd.hasOption("s");
			// Seed Initialization
			if (cmd.hasOption("i")) {
				long newSeed = Integer.valueOf(cmd.getOptionValue("i"));
				SeedGenerator.initialSeed(newSeed);
				LOG.info("Initial Seed: " + newSeed);
			}
			runArena(cmd, refereeCmd, n, logs, swap);

		} catch (Exception exception) {
			LOG.fatal("cg-brutaltester failed to start", exception);
			System.exit(1);
		}
	}

	private static void runArena(CommandLine cmd, String refereeCmd, int n, Path logs, boolean swap) {
		// Players command lines

		var playersFolder = FileSystems.getDefault().getPath(cmd.getOptionValue("f"));
		var playerList = new ArrayList<ArenaPlayer>();

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(playersFolder)) {
			for (Path path : stream) {
				// Iterate over the paths in the directory and print filenames
				LOG.info("Found player %s : %s".formatted(path.getFileName().toString().replace(".jar", ""),
						cmd.getOptionValue("f") + "/" + path.getFileName()));
				playerList.add( //
						new ArenaPlayer(path.getFileName().toString().replace(".jar", ""), //
								"java -jar %s/%s".formatted(cmd.getOptionValue("f"), path.getFileName())));
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		var size = playerList.size();

		ExecutorService es = Executors.newFixedThreadPool(t);
		List<Callable<ArenaStats>> todo = new ArrayList<Callable<ArenaStats>>();

		for (var x = 0; x < size; x++) {
			for (var y = x + 1; y < size; y++) {
				Mutable<Integer> count = new Mutable<>(0);
				var players = Arrays.asList(playerList.get(x), playerList.get(y));

				// Start the threads
				if (cmd.hasOption("o")) {
					// new OldGameThread(1, refereeCmd, playerList, count, playerStats, n, logs,
					// swap).start();
				} else {
					var gameThread = new GameThread(refereeCmd, players, count, n, logs, swap);
					todo.add(gameThread);
				}
			}
		}
		try {
			List<Future<ArenaStats>> answers = es.invokeAll(todo);
			while (!answers.stream().allMatch(f -> f.isDone())) {
				// wait
			}

			var playerStatsMerged = answers.stream().map(f -> f.resultNow()).reduce(new ArenaStats(playerList),
					(a, b) -> ArenaStats.merge(a, b));
			LOG.info("*** End of games ***");
			playerStatsMerged.print();
			System.exit(0);
		} catch (InterruptedException e) {
			LOG.error(e.getStackTrace());
		}
	}
}
