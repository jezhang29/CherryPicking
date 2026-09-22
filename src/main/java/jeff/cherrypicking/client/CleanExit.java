package jeff.cherrypicking.client;

import java.util.List;

import jeff.cherrypicking.CherryPicking;

/**
 * Ends the JVM as soon as the game has finished closing.
 *
 * <p>Vanilla does not exit: {@code Main} returns and waits for the JVM to stop on
 * its own, which it only does once every non-daemon thread has ended. Mods that
 * leave one running - resourcefullib's {@code Scheduler-N} pool inside SkyOcean,
 * SecretRoutes' {@code addMember} HTTP calls - keep it alive until vanilla's
 * shutdown watchdog gives up, writes a "Client shutdown from post-main" crash
 * report and exits with -8. The launcher reads that code as a crash, which is
 * what brings up its crash window and loses the session.
 *
 * <p>By the time {@code post-main} is reached, the world is left, options are
 * saved and the window is gone, so there is nothing left to wait for.
 * {@link System#exit} still runs every shutdown hook.
 */
public final class CleanExit {
	private static volatile boolean enabled = true;

	private CleanExit() {
	}

	public static boolean enabled() {
		return enabled;
	}

	public static void enabled(boolean value) {
		enabled = value;
	}

	/** Called on the main thread once the game has closed normally. */
	public static void afterMain() {
		if (!enabled) {
			return;
		}
		Thread self = Thread.currentThread();
		List<String> lingering = Thread.getAllStackTraces().keySet().stream()
				.filter(thread -> thread != self && thread.isAlive() && !thread.isDaemon())
				.map(Thread::getName)
				.filter(name -> !name.equals("DestroyJavaVM"))
				.sorted()
				.toList();
		if (!lingering.isEmpty()) {
			CherryPicking.LOGGER.info("Exiting past threads that would hold the game open: {}", lingering);
		}
		System.exit(0);
	}
}
