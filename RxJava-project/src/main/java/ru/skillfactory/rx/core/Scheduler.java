package ru.skillfactory.rx.core;

import ru.skillfactory.rx.util.BooleanDisposable;

public interface Scheduler {
    void execute(Runnable task);

    default Disposable schedule(Runnable task) {
        execute(task);
        return new BooleanDisposable();
    }

    void shutdown();
}
