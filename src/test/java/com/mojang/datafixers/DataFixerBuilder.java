package com.mojang.datafixers;

import java.util.concurrent.Executor;

/** Stands in for DataFixerUpper's builder: remembers the executor it was built with. */
public class DataFixerBuilder {

    public static Executor lastExecutor;

    public DataFixer build(Executor executor) {
        lastExecutor = executor;
        // As DataFixerUpper does: one cache warm-up task per type and version.
        executor.execute(() -> {
            throw new AssertionError("warm-up task ran");
        });
        return new DataFixer();
    }
}
