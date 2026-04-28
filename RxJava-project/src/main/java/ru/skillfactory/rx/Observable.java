package ru.skillfactory.rx;

import ru.skillfactory.rx.core.Disposable;
import ru.skillfactory.rx.core.Emitter;
import ru.skillfactory.rx.core.Observer;
import ru.skillfactory.rx.core.Scheduler;
import ru.skillfactory.rx.util.BooleanDisposable;
import ru.skillfactory.rx.util.CompositeDisposable;
import ru.skillfactory.rx.util.SafeObserver;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;

public final class Observable<T> {

    @FunctionalInterface
    public interface OnSubscribe<T> {
        void subscribe(Emitter<T> emitter) throws Exception;
    }

    @FunctionalInterface
    private interface SubscribeAction<T> {
        Disposable subscribe(Observer<? super T> observer);
    }

    private final SubscribeAction<T> onSubscribe;

    private Observable(SubscribeAction<T> onSubscribe) {
        this.onSubscribe = onSubscribe;
    }

    public static <T> Observable<T> create(OnSubscribe<T> source) {
        Objects.requireNonNull(source, "source must not be null");
        return new Observable<>(observer -> {
            SafeObserver<T> safeObserver = new SafeObserver<>(observer);
            CreateEmitter<T> emitter = new CreateEmitter<>(safeObserver);
            try {
                source.subscribe(emitter);
            } catch (Throwable throwable) {
                emitter.onError(throwable);
            }
            return emitter;
        });
    }

    public static <T> Observable<T> just(T... items) {
        return create(emitter -> {
            for (T item : items) {
                if (emitter.isDisposed()) {
                    return;
                }
                emitter.onNext(item);
            }
            emitter.onComplete();
        });
    }

    public Disposable subscribe(Observer<? super T> observer) {
        Objects.requireNonNull(observer, "observer must not be null");
        return onSubscribe.subscribe(observer);
    }

    public <R> Observable<R> map(Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        return new Observable<>(downstream -> Observable.this.subscribe(new Observer<>() {
            @Override
            public void onNext(T item) {
                try {
                    downstream.onNext(mapper.apply(item));
                } catch (Throwable throwable) {
                    downstream.onError(throwable);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                downstream.onError(throwable);
            }

            @Override
            public void onComplete() {
                downstream.onComplete();
            }
        }));
    }

    public Observable<T> filter(Predicate<? super T> predicate) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        return new Observable<>(downstream -> Observable.this.subscribe(new Observer<>() {
            @Override
            public void onNext(T item) {
                try {
                    if (predicate.test(item)) {
                        downstream.onNext(item);
                    }
                } catch (Throwable throwable) {
                    downstream.onError(throwable);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                downstream.onError(throwable);
            }

            @Override
            public void onComplete() {
                downstream.onComplete();
            }
        }));
    }

    public <R> Observable<R> flatMap(Function<? super T, Observable<? extends R>> mapper) {
        Objects.requireNonNull(mapper, "mapper must not be null");
        return new Observable<>(downstream -> {
            CompositeDisposable set = new CompositeDisposable();
            AtomicInteger active = new AtomicInteger(1);
            AtomicBoolean outerDone = new AtomicBoolean(false);

            Disposable upstream = Observable.this.subscribe(new Observer<>() {
                @Override
                public void onNext(T item) {
                    if (set.isDisposed()) {
                        return;
                    }
                    Observable<? extends R> inner;
                    try {
                        inner = Objects.requireNonNull(mapper.apply(item), "flatMap mapper returned null");
                    } catch (Throwable throwable) {
                        set.dispose();
                        downstream.onError(throwable);
                        return;
                    }

                    @SuppressWarnings("unchecked")
                    Observable<R> castInner = (Observable<R>) inner;

                    active.incrementAndGet();
                    Disposable innerDisposable = castInner.subscribe(new Observer<>() {
                        @Override
                        public void onNext(R innerItem) {
                            if (!set.isDisposed()) {
                                downstream.onNext(innerItem);
                            }
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            set.dispose();
                            downstream.onError(throwable);
                        }

                        @Override
                        public void onComplete() {
                            if (active.decrementAndGet() == 0 && outerDone.get() && !set.isDisposed()) {
                                downstream.onComplete();
                            }
                        }
                    });
                    set.add(innerDisposable);
                }

                @Override
                public void onError(Throwable throwable) {
                    set.dispose();
                    downstream.onError(throwable);
                }

                @Override
                public void onComplete() {
                    outerDone.set(true);
                    if (active.decrementAndGet() == 0 && !set.isDisposed()) {
                        downstream.onComplete();
                    }
                }
            });

            set.add(upstream);
            return set;
        });
    }

    public Observable<T> subscribeOn(Scheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler must not be null");
        return new Observable<>(downstream -> {
            CompositeDisposable set = new CompositeDisposable();
            BooleanDisposable gate = new BooleanDisposable();
            set.add(gate);
            Disposable scheduled = scheduler.schedule(() -> {
                if (gate.isDisposed()) {
                    return;
                }
                Disposable upstream = Observable.this.subscribe(downstream);
                set.add(upstream);
            });
            set.add(scheduled);
            return set;
        });
    }

    public Observable<T> observeOn(Scheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler must not be null");
        return new Observable<>(downstream -> {
            CompositeDisposable set = new CompositeDisposable();
            Disposable upstream = Observable.this.subscribe(new Observer<>() {
                @Override
                public void onNext(T item) {
                    scheduler.execute(() -> {
                        if (!set.isDisposed()) {
                            downstream.onNext(item);
                        }
                    });
                }

                @Override
                public void onError(Throwable throwable) {
                    scheduler.execute(() -> {
                        if (!set.isDisposed()) {
                            downstream.onError(throwable);
                            set.dispose();
                        }
                    });
                }

                @Override
                public void onComplete() {
                    scheduler.execute(() -> {
                        if (!set.isDisposed()) {
                            downstream.onComplete();
                            set.dispose();
                        }
                    });
                }
            });
            set.add(upstream);
            return set;
        });
    }

    private static final class CreateEmitter<T> implements Emitter<T> {
        private final SafeObserver<T> downstream;
        private final AtomicBoolean disposed = new AtomicBoolean(false);
        private final AtomicBoolean terminated = new AtomicBoolean(false);

        private CreateEmitter(SafeObserver<T> downstream) {
            this.downstream = downstream;
        }

        @Override
        public void onNext(T item) {
            if (isDisposed() || terminated.get()) {
                return;
            }
            downstream.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            if (isDisposed()) {
                return;
            }
            if (terminated.compareAndSet(false, true)) {
                downstream.onError(throwable);
                dispose();
            }
        }

        @Override
        public void onComplete() {
            if (isDisposed()) {
                return;
            }
            if (terminated.compareAndSet(false, true)) {
                downstream.onComplete();
                dispose();
            }
        }

        @Override
        public void dispose() {
            disposed.set(true);
            downstream.dispose();
        }

        @Override
        public boolean isDisposed() {
            return disposed.get() || downstream.isDisposed();
        }
    }
}
