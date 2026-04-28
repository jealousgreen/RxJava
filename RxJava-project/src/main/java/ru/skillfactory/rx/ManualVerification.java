package ru.skillfactory.rx;

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

public class ManualVerification {
    public static void main(String[] args) throws Exception {
        testBaseSignals();
        testMapFilter();
        testSchedulers();
        testFlatMap();
        testDisposable();
        testErrorHandling();
        System.out.println("ALL_MANUAL_CHECKS_PASSED");
        Schedulers.shutdownAll();
    }

    private static void testBaseSignals() {
        List<Integer> items = new ArrayList<>();
        AtomicReference<String> terminal = new AtomicReference<>("none");

        Observable.<Integer>create(emitter -> {
            emitter.onNext(1);
            emitter.onNext(2);
            emitter.onComplete();
        }).subscribe(new Observer<>() {
            @Override public void onNext(Integer item) { items.add(item); }
            @Override public void onError(Throwable throwable) { throw new AssertionError(throwable); }
            @Override public void onComplete() { terminal.set("complete"); }
        });

        assertEquals(List.of(1, 2), items, "base items");
        assertEquals("complete", terminal.get(), "base complete");
    }

    private static void testMapFilter() {
        List<Integer> items = new ArrayList<>();
        Observable.just(1,2,3,4,5)
                .map(v -> v * 10)
                .filter(v -> v >= 30)
                .subscribe(new Observer<>() {
                    @Override public void onNext(Integer item) { items.add(item); }
                    @Override public void onError(Throwable throwable) { throw new AssertionError(throwable); }
                    @Override public void onComplete() { }
                });
        assertEquals(Arrays.asList(30,40,50), items, "map/filter");
    }

    private static void testSchedulers() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> sourceThread = new AtomicReference<>();
        AtomicReference<String> observeThread = new AtomicReference<>();

        Observable.<Integer>create(emitter -> {
            sourceThread.set(Thread.currentThread().getName());
            emitter.onNext(1);
            emitter.onComplete();
        }).subscribeOn(Schedulers.io())
          .observeOn(Schedulers.single())
          .subscribe(new Observer<>() {
              @Override public void onNext(Integer item) { observeThread.set(Thread.currentThread().getName()); }
              @Override public void onError(Throwable throwable) { throw new AssertionError(throwable); }
              @Override public void onComplete() { latch.countDown(); }
          });

        if (!latch.await(2, TimeUnit.SECONDS)) {
            throw new AssertionError("scheduler latch timeout");
        }
        if (!sourceThread.get().contains("minirx-io")) throw new AssertionError("wrong subscribe thread");
        if (!observeThread.get().contains("minirx-single")) throw new AssertionError("wrong observe thread");
    }

    private static void testFlatMap() {
        List<String> items = new ArrayList<>();
        AtomicReference<String> terminal = new AtomicReference<>("none");
        Observable.just(1,2)
                .flatMap(v -> Observable.just(v + "a", v + "b"))
                .subscribe(new Observer<>() {
                    @Override public void onNext(String item) { items.add(item); }
                    @Override public void onError(Throwable throwable) { throw new AssertionError(throwable); }
                    @Override public void onComplete() { terminal.set("complete"); }
                });
        assertEquals(Arrays.asList("1a","1b","2a","2b"), items, "flatMap items");
        assertEquals("complete", terminal.get(), "flatMap complete");
    }

    private static void testDisposable() throws Exception {
        CopyOnWriteArrayList<Integer> items = new CopyOnWriteArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        Disposable disposable = Observable.<Integer>create(emitter -> {
            for (int i = 1; i <= 10; i++) {
                if (emitter.isDisposed()) break;
                emitter.onNext(i);
                Thread.sleep(20);
            }
            if (!emitter.isDisposed()) emitter.onComplete();
        }).subscribeOn(Schedulers.computation())
          .subscribe(new Observer<>() {
              @Override public void onNext(Integer item) { items.add(item); if (item == 3) latch.countDown(); }
              @Override public void onError(Throwable throwable) { throw new AssertionError(throwable); }
              @Override public void onComplete() { latch.countDown(); }
          });

        if (!latch.await(2, TimeUnit.SECONDS)) throw new AssertionError("disposable latch timeout");
        disposable.dispose();
        Thread.sleep(150);
        assertEquals(Arrays.asList(1,2,3), new ArrayList<>(items), "disposable items");
    }

    private static void testErrorHandling() {
        AtomicReference<String> message = new AtomicReference<>();
        Observable.<Integer>create(emitter -> {
            emitter.onNext(1);
            emitter.onError(new IllegalStateException("boom"));
            emitter.onNext(2);
        }).subscribe(new Observer<>() {
            @Override public void onNext(Integer item) { }
            @Override public void onError(Throwable throwable) { message.set(throwable.getMessage()); }
            @Override public void onComplete() { throw new AssertionError("complete after error"); }
        });
        assertEquals("boom", message.get(), "error message");
    }

    private static void assertEquals(Object expected, Object actual, String label) {
        if ((expected == null && actual != null) || (expected != null && !expected.equals(actual))) {
            throw new AssertionError(label + " expected=" + expected + ", actual=" + actual);
        }
    }
}
