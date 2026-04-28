package ru.skillfactory.rx.util;

import ru.skillfactory.rx.core.Disposable;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CompositeDisposable implements Disposable {
    private final Queue<Disposable> disposables = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean disposed = new AtomicBoolean(false);

    public void add(Disposable disposable) {
        if (disposable == null) {
            return;
        }
        if (disposed.get()) {
            disposable.dispose();
            return;
        }
        disposables.add(disposable);
        if (disposed.get()) {
            disposable.dispose();
        }
    }

    @Override
    public void dispose() {
        if (disposed.compareAndSet(false, true)) {
            Disposable disposable;
            while ((disposable = disposables.poll()) != null) {
                disposable.dispose();
            }
        }
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }
}
