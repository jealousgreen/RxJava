package ru.skillfactory.rx;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import ru.skillfactory.rx.core.Disposable;
import ru.skillfactory.rx.core.Observer;
import ru.skillfactory.rx.schedulers.Schedulers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ObservableTest {

    @AfterAll
    static void shutdownSchedulers() {
        Schedulers.shutdownAll();
    }

    @Test
    @DisplayName("create + subscribe: базовые события приходят в нужном порядке")
    void shouldDeliverBaseSignals() {
        List<Integer> items = new ArrayList<>();
        AtomicReference<String> terminal = new AtomicReference<>("none");

        Observable.<Integer>create(emitter -> {
            emitter.onNext(1);
            emitter.onNext(2);
            emitter.onComplete();
        }).subscribe(new Observer<>() {
            @Override
            public void onNext(Integer item) {
                items.add(item);
            }

            @Override
            public void onError(Throwable throwable) {
                terminal.set("error");
            }

            @Override
            public void onComplete() {
                terminal.set("complete");
            }
        });

        assertEquals(List.of(1, 2), items);
        assertEquals("complete", terminal.get());
    }

    @Test
    @DisplayName("map и filter корректно трансформируют поток")
    void shouldApplyMapAndFilter() {
        List<Integer> result = new ArrayList<>();

        Observable.just(1, 2, 3, 4, 5)
                .map(value -> value * 10)
                .filter(value -> value >= 30)
                .subscribe(new Observer<>() {
                    @Override
                    public void onNext(Integer item) {
                        result.add(item);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        fail(throwable);
                    }

                    @Override
                    public void onComplete() {
                    }
                });

        assertEquals(List.of(30, 40, 50), result);
    }

    @Test
    @DisplayName("subscribeOn и observeOn переключают потоки выполнения")
    void shouldUseDifferentSchedulers() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> subscribeThread = new AtomicReference<>();
        AtomicReference<String> observeThread = new AtomicReference<>();

        Observable.<Integer>create(emitter -> {
                    subscribeThread.set(Thread.currentThread().getName());
                    emitter.onNext(10);
                    emitter.onComplete();
                })
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.single())
                .subscribe(new Observer<>() {
                    @Override
                    public void onNext(Integer item) {
                        observeThread.set(Thread.currentThread().getName());
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        fail(throwable);
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(subscribeThread.get());
        assertNotNull(observeThread.get());
        assertTrue(subscribeThread.get().contains("minirx-io"));
        assertTrue(observeThread.get().contains("minirx-single"));
        assertNotEquals(subscribeThread.get(), observeThread.get());
    }

    @Test
    @DisplayName("flatMap разворачивает внутренние Observable и завершает поток после всех inner")
    void shouldFlatMapAndWaitAllInnerStreams() {
        List<String> result = new ArrayList<>();
        AtomicReference<String> terminal = new AtomicReference<>("none");

        Observable.just(1, 2)
                .flatMap(value -> Observable.just(value + "a", value + "b"))
                .subscribe(new Observer<>() {
                    @Override
                    public void onNext(String item) {
                        result.add(item);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        terminal.set("error");
                    }

                    @Override
                    public void onComplete() {
                        terminal.set("complete");
                    }
                });

        assertEquals(Arrays.asList("1a", "1b", "2a", "2b"), result);
        assertEquals("complete", terminal.get());
    }

    @Test
    @DisplayName("Disposable отменяет подписку и останавливает получение элементов")
    void shouldDisposeSubscription() throws InterruptedException {
        CopyOnWriteArrayList<Integer> result = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);

        Disposable disposable = Observable.<Integer>create(emitter -> {
                    for (int i = 1; i <= 10; i++) {
                        if (emitter.isDisposed()) {
                            break;
                        }
                        emitter.onNext(i);
                        Thread.sleep(30);
                    }
                    if (!emitter.isDisposed()) {
                        emitter.onComplete();
                    }
                })
                .subscribeOn(Schedulers.computation())
                .subscribe(new Observer<>() {
                    @Override
                    public void onNext(Integer item) {
                        result.add(item);
                        if (item == 3) {
                            latch.countDown();
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        fail(throwable);
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        disposable.dispose();
        Thread.sleep(150);

        assertTrue(result.size() <= 3, "После dispose не должно прийти заметно больше элементов");
        assertEquals(List.of(1, 2, 3), result);
    }

    @Test
    @DisplayName("ошибка из источника доходит до onError и останавливает поток")
    void shouldPropagateSourceError() {
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicInteger received = new AtomicInteger();

        Observable.<Integer>create(emitter -> {
            emitter.onNext(1);
            emitter.onError(new IllegalStateException("boom"));
            emitter.onNext(2);
        }).subscribe(new Observer<>() {
            @Override
            public void onNext(Integer item) {
                received.incrementAndGet();
            }

            @Override
            public void onError(Throwable throwable) {
                errorRef.set(throwable);
            }

            @Override
            public void onComplete() {
                fail("onComplete не должен вызываться после ошибки");
            }
        });

        assertEquals(1, received.get());
        assertNotNull(errorRef.get());
        assertEquals("boom", errorRef.get().getMessage());
    }

    @Test
    @DisplayName("ошибка внутри map передается в onError")
    void shouldPropagateMapError() {
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Observable.just(1, 2, 3)
                .map(value -> {
                    if (value == 2) {
                        throw new IllegalArgumentException("bad value");
                    }
                    return value;
                })
                .subscribe(new Observer<>() {
                    @Override
                    public void onNext(Integer item) {
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        errorRef.set(throwable);
                    }

                    @Override
                    public void onComplete() {
                        fail("Поток не должен завершиться успешно");
                    }
                });

        assertNotNull(errorRef.get());
        assertEquals("bad value", errorRef.get().getMessage());
    }

    @Test
    @DisplayName("computation scheduler выполняет работу в пуле вычислительных потоков")
    void shouldUseComputationScheduler() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> threadName = new AtomicReference<>();

        Observable.just(1)
                .observeOn(Schedulers.computation())
                .subscribe(new Observer<>() {
                    @Override
                    public void onNext(Integer item) {
                        threadName.set(Thread.currentThread().getName());
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        fail(throwable);
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }
                });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        assertNotNull(threadName.get());
        assertTrue(threadName.get().contains("minirx-comp"));
    }
}
