# AGENTS.md

Проект: Spring MVC веб-приложение на Spring Boot 3 (Java 17, Maven).

## Команды

- Запуск приложения (dev-сервер на http://localhost:8080): `BTC_STORAGE_DIR=/Users/azatakhunov/temp/btc mvn spring-boot:run`
- Запуск приложения (regtest, для тестирования BTC-переводов): `BTC_STORAGE_DIR=/Users/azatakhunov/temp/btc BTC_NETWORK=regtest mvn spring-boot:run`
- Запуск приложения с портом 8080 свободным: перед запуском `lsof -ti:8080 | xargs kill -9`
- Сборка: `mvn clean package`
- Компиляция без сборки артефакта: `mvn compile`
- Тесты: `mvn test`
- Один тестовый класс: `mvn test -Dtest=HomeControllerTest`
- Один тестовый метод: `mvn test -Dtest=HomeControllerTest#home_returnsViewWithName`

Порядок проверки перед коммитом: `mvn test` → `mvn package` (или `mvn verify`).

Важно: проект требует JDK 17, но Maven по умолчанию может подхватить более новый JDK из системы и упасть с `release version 17 not supported`. Запускать с `JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn ...` или выставить `JAVA_HOME` заранее.

## Архитектура

- Точка входа: `src/main/java/com/example/springmvcapp/Application.java` (`@SpringBootApplication`).
- Контроллеры MVC живут в пакете `com.example.springmvcapp.web`, возвращают имена Thymeleaf-шаблонов.
- Шаблоны: `src/main/resources/templates/` (Thymeleaf).
- Конфигурация: `src/main/resources/application.properties`.

## Соглашения

- Базовый пакет: `com.example.springmvcapp`. Веб-слой — в подпакете `web`.
- Контроллеры используют аннотацию `@Controller` + Thymeleaf; для REST-эндпоинтов добавлять `@RestController` в новый класс.
- Тесты веб-слоя — `@WebMvcTest` с `MockMvc`, в `src/test/java` с тем же пакетом.
- Java 17; Spring Boot управляет версиями зависимостей через `spring-boot-starter-parent` — не указывать версии зависимостей явно без необходимости.

## Статические ресурсы

- `src/main/resources/static/vendor/` — Three.js r128, OBJLoader, MTLLoader (локально, без CDN).
- `src/main/resources/static/room/` — 3D-модель комнаты: `room.obj`, `room.mtl`, `textures/`.
- `src/main/resources/static/photos/` — фотографии для 3D-сцены архитектуры.
- Маршруты: `/` — Canvas 2D сцена архитектуры Spring MVC; `/room` — Three.js 3D-комната.

## Деплой (Render.com)

- Dockerfile: мультистейдж (Maven сборка на `maven:3.9-eclipse-temurin-17` → запуск на `eclipse-temurin:17-jre`).
- `render.yaml`: web-service, plan: free, runtime: docker.
- Порт: `server.port=${PORT:8080}` — Render передаёт `PORT` через env.
- Health check: `GET /` (маршрут HomeController).
- Локальная сборка Docker: `docker build -t spring-mvc-3d .` → `docker run -p 8080:8080 spring-mvc-3d`.

## Локальное тестирование BTC-переводов (regtest)

### 1. Запуск bitcoind в режиме regtest

```bash
mkdir -p ~/temp/bitcoin-regtest
bitcoind -regtest -datadir=~/temp/bitcoin-regtest -rpcport=18443 -rpcuser=user -rpcpassword=pass -daemon -fallbackfee=0.00001
```

Создать кошелёк и сгенерировать блоки (101 блок — coinbase созревает после 100 подтверждений):

```bash
BCLI="bitcoin-cli -regtest -datadir=~/temp/bitcoin-regtest -rpcport=18443 -rpcuser=user -rpcpassword=pass"
$BCLI createwallet testwallet
ADDR=$($BCLI getnewaddress)
$BCLI generatetoaddress 101 $ADDR
$BCLI getbalance   # ~50 BTC
```

### 2. Запуск Spring-приложения

```bash
BTC_STORAGE_DIR=~/temp/btc BTC_NETWORK=regtest JAVA_HOME=$(/usr/libexec/java_home -v 17) mvn spring-boot:run
```

### 3. Создать кошелёк и получить адрес

```bash
# Создать кошелёк
curl -s -X POST http://localhost:8080/api/btc/wallet/test-user-1 | python3 -m json.tool

# Получить receive-адрес
RECV=$(curl -s -X POST http://localhost:8080/api/btc/wallet/test-user-1/address | python3 -c "import sys,json;print(json.load(sys.stdin)['address'])")
echo "address: $RECV"
```

### 4. Отправить сатоши из bitcoind на адрес кошелька

```bash
# Отправить 50000 сатоши (0.0005 BTC)
TXID=$($BCLI sendtoaddress "$RECV" 0.0005)
echo "txid: $TXID"

# Сгенерировать 1 блок (подтвердить транзакцию)
$BCLI generatetoaddress 1 "$($BCLI getnewaddress)"

# Получить hex транзакции
HEX=$($BCLI gettransaction "$TXID" | python3 -c "import sys,json;print(json.load(sys.stdin)['hex'])")
```

### 5. Импортировать транзакцию в кошелёк приложения

```bash
curl -s -X POST http://localhost:8080/api/btc/wallet/test-user-1/import-tx \
  -H 'Content-Type: application/json' \
  -d "{\"hex\":\"$HEX\",\"depth\":1}" | python3 -m json.tool
```

### 6. Проверить баланс и транзакции

```bash
curl -s http://localhost:8080/api/btc/wallet/test-user-1/balance | python3 -m json.tool
curl -s http://localhost:8080/api/btc/wallet/test-user-1/transactions | python3 -m json.tool
```

### 7. Остановка

```bash
$BCLI stop
lsof -ti:8080 | xargs kill -9
```

### Переменные окружения

| Переменная | По умолчанию | Локально | Amvera |
|---|---|---|---|
| `BTC_STORAGE_DIR` | `/data` | `~/temp/btc` | `/data` |
| `BTC_NETWORK` | `mainnet` | `regtest` (для тестов) | `mainnet` |
