package dev.dove.agent.boot;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Minecraft 1.14 to 1.18 build their DataFixerUpper (the rules that upgrade worlds saved by older
 * versions) with {@code DataFixerBuilder.build(executor)}, which also hands the executor one task
 * per data type and version to warm DataFixerUpper's rule cache: each task looks a rule up and
 * rewrites a type with it, then drops the result. Thousands of them run on the background threads
 * while the game loads (70% of the CPU samples before the title screen on 1.14.4), for rules a game
 * only needs when it opens a world from an older version.
 *
 * <p>The executor given here drops those tasks, as the LazyDFU mod does: DataFixerUpper builds and
 * caches each rule the first time a fix needs it, with the same result. Newer Minecraft versions
 * made the same change themselves.
 *
 * <p>Minecraft's classes call this, and Fabric's class loader only lets them see agent classes on
 * the bootstrap class path, so this class is put there ({@link BootClasses}) and must not use any
 * other agent class.
 */
public final class DataFixerHooks {

    public static final String OWNER = "dev/dove/agent/boot/DataFixerHooks";

    private static final AtomicInteger SKIPPED = new AtomicInteger();

    /** Runs nothing: each task only warms a cache that fills itself when used. */
    static final Executor SKIP_WARM_UP = task -> SKIPPED.incrementAndGet();

    private DataFixerHooks() {
    }

    /** Replaces the executor passed to {@code DataFixerBuilder.build(Executor)}. */
    public static Executor lazy(Executor original) {
        return SKIP_WARM_UP;
    }

    /** Warm-up tasks skipped so far. */
    public static int skipped() {
        return SKIPPED.get();
    }
}
