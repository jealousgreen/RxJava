package ru.skillfactory.rx.core;

public interface Emitter<T> extends Disposable {
    void onNext(T item);
    void onError(Throwable throwable);
    void onComplete();
}
