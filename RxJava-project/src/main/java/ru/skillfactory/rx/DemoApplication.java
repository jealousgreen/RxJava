package ru.skillfactory.rx;

import ru.skillfactory.rx.core.Observer;
import ru.skillfactory.rx.schedulers.Schedulers;

public class DemoApplication {
    public static void main(String[] args) throws InterruptedException {
        Observable.just(1, 2, 3, 4, 5)
                .map(value -> value * 10)
                .filter(value -> value >= 30)
                .flatMap(value -> Observable.just("item=" + value, "square=" + (value * value)))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.single())
                .subscribe(new Observer<>() {
                    @Override
                    public void onNext(String item) {
                        System.out.println(Thread.currentThread().getName() + " -> " + item);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        System.err.println("Ошибка: " + throwable.getMessage());
                    }

                    @Override
                    public void onComplete() {
                        System.out.println("Поток завершён");
                    }
                });

        Thread.sleep(1000);
        Schedulers.shutdownAll();
    }
}
