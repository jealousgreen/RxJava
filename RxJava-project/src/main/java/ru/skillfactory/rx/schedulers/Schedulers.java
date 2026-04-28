package ru.skillfactory.rx.schedulers;

import ru.skillfactory.rx.core.Scheduler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public final class Schedulers {
    private static final Scheduler IO = new ExecutorScheduler(
            Executors.newCachedThreadPool(r -> namedThread("minirx-io", r))
    );

    private static final Scheduler COMPUTATION = new ExecutorScheduler(
            Executors.newFixedThreadPool(
                    Math.max(2, Runtime.getRuntime().availableProcessors()),
                    r -> namedThread("minirx-comp", r)
            )
    );

    private static final Scheduler SINGLE = new ExecutorScheduler(
            Executors.newSingleThreadExecutor(r -> namedThread("minirx-single", r))
    );

    private Schedulers() {
    }

    public static Scheduler io() {
        return IO;
    }

    public static Scheduler computation() {
        return COMPUTATION;
    }

    public static Scheduler single() {
        return SINGLE;
    }

    public static void shutdownAll() {
        IO.shutdown();
        COMPUTATION.shutdown();
        SINGLE.shutdown();
    }

    private static Thread namedThread(String prefix, Runnable runnable) {
        AtomicInteger counter = Holder.COUNTER;
        Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }

    private static final class Holder {
        private static final AtomicInteger COUNTER = new AtomicInteger();
    }
}
