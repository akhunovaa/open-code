package com.example.springmvcapp.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.springmvcapp.service.BitcoinWalletService;
import com.example.springmvcapp.service.BitcoinWalletService.SendResult;
import com.example.springmvcapp.service.BitcoinWalletService.TxInfo;
import com.example.springmvcapp.service.BitcoinWalletService.WalletInfo;
import org.bitcoinj.wallet.UnreadableWalletException;

/**
 * REST-контроллер для работы с биткоин-кошельками через библиотеку bitcoinj.
 *
 * <p>Предоставляет HTTP API для создания кошельков, получения адресов и баланса,
 * просмотра транзакций и оффлайн-отправки средств. Все кошельки работают в сети
 * {@code regtest} и сериализуются в protobuf-файлы в каталоге
 * {@code /Users/azatakhunov/temp/btc}.</p>
 *
 * <p>Список эндпоинтов:</p>
 * <ul>
 *     <li>{@code GET /api/btc/wallets} — список идентификаторов кошельков;</li>
 *     <li>{@code POST /api/btc/wallet} — создание нового кошелька;</li>
 *     <li>{@code GET /api/btc/wallet/{id}} — информация о кошельке;</li>
 *     <li>{@code POST /api/btc/wallet/{id}/address} — новый receive-адрес;</li>
 *     <li>{@code GET /api/btc/wallet/{id}/balance} — доступный баланс;</li>
 *     <li>{@code GET /api/btc/wallet/{id}/transactions} — список транзакций;</li>
 *     <li>{@code POST /api/btc/wallet/{id}/send} — отправка средств.</li>
 * </ul>
 *
 * @see BitcoinWalletService
 */
@RestController
public class BitcoinWalletController {

    private final BitcoinWalletService walletService;

    /**
     * Создаёт контроллер с заданным сервисом кошельков.
     *
     * @param walletService сервис, реализующий логику работы с bitcoinj-кошельками
     */
    public BitcoinWalletController(BitcoinWalletService walletService) {
        this.walletService = walletService;
    }

    /**
     * Возвращает список идентификаторов всех сохранённых кошельков.
     *
     * <p>Сканирует каталог хранения {@code /Users/azatakhunov/temp/btc} и возвращает
     * имена файлов без расширения {@code .wallet}. Если каталога не существует,
     * возвращается пустой список.</p>
     *
     * @return список идентификаторов кошельков
     * @throws IOException если не удалось прочитать каталог хранения
     */
    @GetMapping("/api/btc/wallets")
    public List<String> wallets() throws IOException {
        return walletService.listWallets();
    }

    /**
     * Создаёт новый детерминированный (BIP32/BIP39) кошелёк и возвращает его данные.
     *
     * <p>Кошелёк сохраняется в файл {@code <id>.wallet} и немедленно возвращается
     * полная информация о нём (идентификатор, сеть, мнемоническая фраза, баланс,
     * receive-адрес и выданные адреса).</p>
     *
     * @return {@link WalletInfo} с данными созданного кошелька
     * @throws IOException               если не удалось создать каталог или сохранить кошелёк
     * @throws UnreadableWalletException если созданный кошелёк не удалось загрузить
     */
    @PostMapping("/api/btc/wallet")
    public WalletInfo create() throws IOException, UnreadableWalletException {
        return walletService.getWallet(walletService.createWallet());
    }

    /**
     * Создаёт новый детерминированный кошелёк с указанным идентификатором.
     *
     * <p>Идентификатор задаётся клиентом и используется как имя файла кошелька.
     * Это позволяет, например, хранить кошелёк под идентификатором конкретного
     * пользователя в каталоге {@code /Users/azatakhunov/temp/btc}. Если кошелёк
     * с таким идентификатором уже существует, возвращается его текущее состояние.</p>
     *
     * @param id идентификатор кошелька (имя файла без расширения)
     * @return {@link WalletInfo} с данными созданного или существующего кошелька
     * @throws IOException               если не удалось создать каталог или сохранить кошелёк
     * @throws UnreadableWalletException если кошелёк не удалось загрузить
     */
    @PostMapping("/api/btc/wallet/{id}")
    public WalletInfo create(@PathVariable String id) throws IOException, UnreadableWalletException {
        return walletService.getWallet(walletService.createWallet(id));
    }

    /**
     * Возвращает информацию о кошельке по его идентификатору.
     *
     * <p>Если файл кошелька существует, он загружается с диска; в противном случае
     * создаётся новый пустой кошелёк с указанным идентификатором.</p>
     *
     * @param id идентификатор кошелька (имя файла без расширения)
     * @return {@link WalletInfo} с данными кошелька
     * @throws IOException               если не удалось прочитать файл кошелька
     * @throws UnreadableWalletException если файл кошелька повреждён или не читается
     */
    @GetMapping("/api/btc/wallet/{id}")
    public WalletInfo get(@PathVariable String id) throws IOException, UnreadableWalletException {
        return walletService.getWallet(id);
    }

    /**
     * Генерирует новый «свежий» receive-адрес для кошелька.
     *
     * <p>Каждый вызов возвращает адрес, который ещё не выдавался ранее (ключ
     * помечается как использованный в цепочке детерминированных ключей).</p>
     *
     * @param id идентификатор кошелька
     * @return карта с одним полем {@code address} — новый адрес кошелька
     * @throws IOException               если не удалось загрузить/сохранить кошелёк
     * @throws UnreadableWalletException если файл кошелька не читается
     */
    @PostMapping("/api/btc/wallet/{id}/address")
    public Map<String, String> freshAddress(@PathVariable String id) throws IOException, UnreadableWalletException {
        return Map.of("address", walletService.freshAddress(id));
    }

    /**
     * Возвращает доступный баланс кошелька в сатоши.
     *
     * <p>Используется баланс {@link org.bitcoinj.wallet.Wallet.BalanceType#AVAILABLE} —
     * средства, которые можно потратить без учёта неподтверждённых транзакций.</p>
     *
     * @param id идентификатор кошелька
     * @return карта с полем {@code balanceSat} — баланс в сатоши
     * @throws IOException               если не удалось загрузить кошелёк
     * @throws UnreadableWalletException если файл кошелька не читается
     */
    @GetMapping("/api/btc/wallet/{id}/balance")
    public Map<String, Long> balance(@PathVariable String id) throws IOException, UnreadableWalletException {
        return Map.of("balanceSat", walletService.balance(id));
    }

    /**
     * Возвращает транзакции кошелька, отсортированные по времени.
     *
     * <p>Для каждой транзакции возвращается её идентификатор, изменение баланса
     * (в сатоши), комиссия и список выходов (адрес + сумма).</p>
     *
     * @param id идентификатор кошелька
     * @return список {@link TxInfo} с транзакциями кошелька
     * @throws IOException               если не удалось загрузить кошелёк
     * @throws UnreadableWalletException если файл кошелька не читается
     */
    @GetMapping("/api/btc/wallet/{id}/transactions")
    public List<TxInfo> transactions(@PathVariable String id) throws IOException, UnreadableWalletException {
        return walletService.transactions(id);
    }

    /**
     * Создаёт и подписывает транзакцию отправки средств без трансляции в сеть.
     *
     * <p>Транзакция формируется методом {@code sendCoinsOffline}: она подписывается
     * ключами кошелька и сохраняется, но не рассылается по сети Bitcoin. При
     * нехватке средств на балансе возвращается ответ с кодом {@code 400}.</p>
     *
     * @param id   идентификатор кошелька-отправителя
     * @param body тело запроса с адресом получателя {@code to} и суммой в сатоши
     *             {@code amountSat}
     * @return {@link SendResult} с идентификатором транзакции, её hex-представлением
     *         и комиссией
     * @throws IOException               если не удалось загрузить/сохранить кошелёк
     * @throws UnreadableWalletException если файл кошелька не читается
     * @see BitcoinWalletService#send(String, String, long)
     */
    @PostMapping("/api/btc/wallet/{id}/send")
    public SendResult send(@PathVariable String id, @RequestBody SendRequest body) throws IOException, UnreadableWalletException {
        return walletService.send(id, body.getTo(), body.getAmountSat());
    }

    /**
     * Обрабатывает ошибки ввода-вывода и возвращает ответ с кодом {@code 500}.
     *
     * @param e перехваченное исключение ввода-вывода
     * @return ответ со статусом {@code 500 Internal Server Error} и полем {@code error}
     */
    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> handleIo(IOException e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Ошибка ввода-вывода: " + e.getMessage()));
    }

    /**
     * Обрабатывает ошибки валидации запроса и нечитаемые кошельки.
     *
     * <p>Отвечает кодом {@code 400 Bad Request}, например, когда указан
     * некорректный адрес получателя или на балансе не хватает средств.</p>
     *
     * @param e перехваченное исключение
     * @return ответ со статусом {@code 400 Bad Request} и полем {@code error}
     */
    @ExceptionHandler({ IllegalArgumentException.class, UnreadableWalletException.class })
    public ResponseEntity<Map<String, String>> handleBadRequest(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    /**
     * Тело запроса для отправки средств через {@link #send}.
     */
    public static class SendRequest {
        private String to;
        private long amountSat;

        /**
         * @return адрес получателя в формате сети кошелька (bech32)
         */
        public String getTo() { return to; }

        /**
         * @param to адрес получателя в формате сети кошелька (bech32)
         */
        public void setTo(String to) { this.to = to; }

        /**
         * @return сумма перевода в сатоши
         */
        public long getAmountSat() { return amountSat; }

        /**
         * @param amountSat сумма перевода в сатоши
         */
        public void setAmountSat(long amountSat) { this.amountSat = amountSat; }
    }
}