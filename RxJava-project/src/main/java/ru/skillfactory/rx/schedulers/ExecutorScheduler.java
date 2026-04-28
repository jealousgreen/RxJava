package ru.skillfactory.rx.schedulers;

import ru.skillfactory.rx.core.Scheduler;

import ru.skillfactory.rx.core.Disposable;
import ru.skillfactory.rx.util.BooleanDisposable;
import ru.skillfactory.rx.util.FutureDisposable;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

public final class ExecutorScheduler implements Scheduler {
    private final ExecutorService executorService;

    public ExecutorScheduler(ExecutorService executorService) {
        this.executorService = Objects.requireNonNull(executorService, "executorService must not be null");
    }

    @Override
    public void execute(Runnable task) {
        try {
            executorService.execute(task);
        } catch (RejectedExecutionException ignored) {
            // Планировщик уже остановлен; для учебного проекта достаточно молча проигнорировать задачу.
        }
    }

    @Override
    public Disposable schedule(Runnable task) {
        try {
            Future<?> future = executorService.submit(task);
            return new FutureDisposable(future);
        } catch (RejectedExecutionException ignored) {
            return new BooleanDisposable();
        }
    }

    @Override
    public void shutdown() {
        executorService.shutdownNow();
    }
}
