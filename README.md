# RxJava

Учебная реализация базовых идей RxJava:
- `Observer` и `Observable`
- операторы `map`, `filter`, `flatMap`
- `Disposable`
- планировщики `io`, `computation`, `single`
- методы `subscribeOn` и `observeOn`
- обработка ошибок
- unit-тесты JUnit 5

## Сборка и тесты

```bash
mvn clean test
```

## Демонстрационный запуск

```bash
mvn clean package
java -jar target/minirx-1.0.0.jar
```
