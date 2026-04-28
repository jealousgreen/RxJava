package ru.skillfactory.rx.util;

import ru.skillfactory.rx.core.Disposable;
import ru.skillfactory.rx.core.Observer;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SafeObserver<T> implements Observer<T>, Disposable {
    private final Observer<? super T> actual;
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final AtomicBoolean terminated = new AtomicBoolean(false);

    public SafeObserver(Observer<? super T> actual) {
        this.actual = Objects.requireNonNull(actual, "observer must not be null");
    }

    @Override
    public void onNext(T item) {
        if (isDisposed() || terminated.get()) {
            return;
        }
        if (item == null) {
            onError(new NullPointerException("Observable does not support null items"));
            return;
        }
        actual.onNext(item);
    }

    @Override
    public void onError(Throwable throwable) {
        Throwable error = throwable == null ? new NullPointerException("Throwable must not be null") : throwable;
        if (isDisposed()) {
            return;
        }
        if (terminated.compareAndSet(false, true)) {
            disposed.set(true);
            actual.onError(error);
        }
    }

    @Override
    public void onComplete() {
        if (isDisposed()) {
            return;
        }
        if (terminated.compareAndSet(false, true)) {
            disposed.set(true);
            actual.onComplete();
        }
    }

    @Override
    public void dispose() {
        disposed.set(true);
    }

    @Override
    public boolean isDisposed() {
        return disposed.get();
    }
}
