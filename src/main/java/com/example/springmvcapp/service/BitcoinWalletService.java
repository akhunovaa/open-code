package com.example.springmvcapp.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bitcoinj.base.Address;
import org.bitcoinj.base.AddressParser;
import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.base.Coin;
import org.bitcoinj.base.ScriptType;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.bitcoinj.wallet.WalletTransaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.springmvcapp.service.dto.OutputInfo;
import com.example.springmvcapp.service.dto.SendResult;
import com.example.springmvcapp.service.dto.TxInfo;
import com.example.springmvcapp.service.dto.WalletInfo;

/**
 * Сервис для управления биткоин-кошельками через библиотеку bitcoinj.
 *
 * <p>Управляет детерминированными (BIP32/BIP39) кошельками с типом адресов
 * {@code P2WPKH}. Сеть задаётся свойством {@code btc.network} (по умолчанию
 * {@code mainnet}; для тестирования — {@code regtest}, {@code testnet} или
 * {@code signet}). Каждый кошелёк сериализуется в protobuf-файл и хранится
 * в каталоге, заданном свойством {@code btc.storage-dir} (по умолчанию
 * {@code /data}).</p>
 *
 * <p>Кошельки кэшируются в памяти и автоматически сохраняются на диск
 * (автосейв с задержкой 500 мс). Все методы синхронизированы для защиты
 * от гонок при параллельной записи. Для mainnet автоматически запускается
 * P2P-подключение через {@link BtcP2pManager}.</p>
 *
 * <p><b>SOLID:</b></p>
 * <ul>
 *   <li><b>S</b> — сервис отвечает только за операции с кошельками;
 *       P2P-управление вынесено в {@link BtcP2pManager},
 *       DTO — в {@code service.dto}</li>
 *   <li><b>O</b> — DTO являются {@code record}, расширение через новые записи
 *       без изменения существующих</li>
 *   <li><b>L</b> — все методы можно переопределить в подклассе без нарушения
 *       контракта</li>
 *   <li><b>I</b> — публичный API разделён на группы: создание/листинг,
 *       адреса, баланс, транзакции, отправка</li>
 *   <li><b>D</b> — зависит от абстракций ({@link BitcoinNetwork},
 *       {@link BtcP2pManager}), а не от конкретных реализаций</li>
 * </ul>
 *
 * @see BtcP2pManager
 * @see <a href="https://bitcoinj.org/javadoc/0.17.1/">bitcoinj 0.17.1 javadoc</a>
 */
@Service
public class BitcoinWalletService {

    private static final int CONFIRMATION_THRESHOLD = 3;
    private static final ScriptType SCRIPT_TYPE = ScriptType.P2WPKH;

    private final Path storageDir;
    private final BitcoinNetwork network;
    private final AddressParser addressParser;
    private final Map<String, Wallet> cache = new ConcurrentHashMap<>();
    private final BtcP2pManager p2pManager;

    /**
     * Создаёт сервис кошельков.
     *
     * @param storageDir путь к каталогу хранения (свойство {@code btc.storage-dir})
     * @param network    имя сети: {@code mainnet}, {@code regtest}, {@code testnet},
     *                   {@code signet} (свойство {@code btc.network})
     */
    public BitcoinWalletService(
            @Value("${btc.storage-dir:/data}") String storageDir,
            @Value("${btc.network:mainnet}") String network) {
        this.storageDir = Paths.get(storageDir);
        this.network = BitcoinNetwork.valueOf(network.toUpperCase());
        this.addressParser = AddressParser.getDefault(this.network);
        this.p2pManager = new BtcP2pManager(this.network, this.storageDir);
        this.p2pManager.start();
    }

    /**
     * Возвращает файл кошелька по его идентификатору.
     *
     * @param id идентификатор кошелька
     * @return файл {@code <storageDir>/<id>.wallet}
     */
    private synchronized File fileFor(String id) {
        return storageDir.resolve(id + ".wallet").toFile();
    }

    /**
     * Загружает кошелёк из кэша или с диска, при необходимости создавая новый.
     *
     * <p>Если кошелёк уже есть в кэше, возвращается он. Иначе, если файл
     * {@code <id>.wallet} существует, кошелёк десериализуется; в противном случае
     * создаётся новый детерминированный кошелёк. После загрузки включается
     * автосейв, кошелёк регистрируется в P2P-сети (для mainnet) и помещается
     * в кэш.</p>
     *
     * @param id идентификатор кошелька
     * @return загруженный или созданный кошелёк
     * @throws IOException               при ошибке чтения/записи диска
     * @throws UnreadableWalletException если файл кошелька повреждён
     */
    private synchronized Wallet loadOrCreate(String id) throws IOException, UnreadableWalletException {
        Wallet cached = cache.get(id);
        if (cached != null) {
            return cached;
        }
        File file = fileFor(id);
        Wallet wallet;
        if (file.exists()) {
            try {
                wallet = Wallet.loadFromFile(file);
            } catch (UnreadableWalletException e) {
                file.delete();
                wallet = Wallet.createDeterministic(network, SCRIPT_TYPE);
            }
        } else {
            wallet = Wallet.createDeterministic(network, SCRIPT_TYPE);
        }
        wallet.autosaveToFile(file, java.time.Duration.ofMillis(500), null);
        p2pManager.addWallet(wallet);
        cache.put(id, wallet);
        return wallet;
    }

    // ─── Создание и листинг ───────────────────────────────────────────

    /**
     * Создаёт новый кошелёк со случайным UUID-идентификатором.
     *
     * @return идентификатор созданного кошелька
     * @throws IOException               если не удалось создать каталог или сохранить файл
     * @throws UnreadableWalletException если созданный кошелёк не удалось загрузить
     */
    public synchronized String createWallet() throws IOException, UnreadableWalletException {
        return createWallet(UUID.randomUUID().toString());
    }

    /**
     * Создаёт новый кошелёк с указанным идентификатором.
     *
     * @param id идентификатор кошелька (имя файла без расширения)
     * @return идентификатор созданного кошелька
     * @throws IOException               если не удалось создать каталог или сохранить файл
     * @throws UnreadableWalletException если созданный кошелёк не удалось загрузить
     */
    public synchronized String createWallet(String id) throws IOException, UnreadableWalletException {
        Files.createDirectories(storageDir);
        loadOrCreate(id).saveToFile(fileFor(id));
        return id;
    }

    /**
     * Возвращает идентификаторы всех сохранённых кошельков.
     *
     * @return список идентификаторов (пустой, если каталога нет)
     * @throws IOException если не удалось прочитать каталог хранения
     */
    public synchronized List<String> listWallets() throws IOException {
        if (!Files.isDirectory(storageDir)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        try (var stream = Files.list(storageDir)) {
            stream.filter(p -> p.toString().endsWith(".wallet"))
                    .forEach(p -> ids.add(p.getFileName().toString().replace(".wallet", "")));
        }
        return ids;
    }

    // ─── Информация о кошельке ───────────────────────────────────────

    /**
     * Возвращает полную информацию о кошельке.
     *
     * @param id идентификатор кошелька
     * @return {@link WalletInfo} с данными кошелька
     * @throws IOException               если не удалось загрузить кошелёк
     * @throws UnreadableWalletException если файл кошелька не читается
     */
    public synchronized WalletInfo getWallet(String id) throws IOException, UnreadableWalletException {
        Wallet wallet = loadOrCreate(id);
        List<String> addresses = wallet.getIssuedReceiveAddresses().stream()
                .map(Address::toString)
                .toList();
        String current = addresses.isEmpty() ? null : addresses.get(addresses.size() - 1);
        return new WalletInfo(
                id,
                network.toString(),
                wallet.getKeyChainSeed() != null ? wallet.getKeyChainSeed().getMnemonicString() : null,
                wallet.getBalance(BalanceType.AVAILABLE).toSat(),
                current,
                addresses,
                wallet.getTransactionsByTime().size());
    }

    /**
     * Генерирует свежий receive-адрес кошелька.
     *
     * @param id идентификатор кошелька
     * @return новый адрес в формате bech32
     * @throws IOException               если не удалось загрузить кошелёк
     * @throws UnreadableWalletException если файл кошелька не читается
     */
    public synchronized String freshAddress(String id) throws IOException, UnreadableWalletException {
        return loadOrCreate(id).freshReceiveAddress().toString();
    }

    // ─── Баланс ──────────────────────────────────────────────────────

    /**
     * Возвращает доступный баланс кошелька в сатоши.
     *
     * @param id идентификатор кошелька
     * @return баланс в сатоши ({@link BalanceType#AVAILABLE})
     * @throws IOException               если не удалось загрузить кошелёк
     * @throws UnreadableWalletException если файл кошелька не читается
     */
    public synchronized long balance(String id) throws IOException, UnreadableWalletException {
        return loadOrCreate(id).getBalance(BalanceType.AVAILABLE).toSat();
    }

    /**
     * Возвращает подтверждённый баланс — сумму входящих транзакций
     * с подтверждениями ≥ {@value #CONFIRMATION_THRESHOLD} блоков.
     *
     * @param id идентификатор кошелька
     * @return подтверждённый баланс в сатоши
     * @throws IOException               если не удалось загрузить кошелёк
     * @throws UnreadableWalletException если файл кошелька не читается
     */
    public synchronized long confirmedBalance(String id) throws IOException, UnreadableWalletException {
        Wallet wallet = loadOrCreate(id);
        long sum = 0;
        for (Transaction tx : wallet.getTransactionsByTime()) {
            if (isConfirmed(tx) && tx.getValue(wallet).toSat() > 0) {
                sum += tx.getValue(wallet).toSat();
            }
        }
        return sum;
    }

    // ─── Транзакции ──────────────────────────────────────────────────

    /**
     * Возвращает все транзакции кошелька, отсортированные по времени.
     *
     * @param id идентификатор кошелька
     * @return список {@link TxInfo}
     * @throws IOException               если не удалось загрузить кошелёк
     * @throws UnreadableWalletException если файл кошелька не читается
     */
    public synchronized List<TxInfo> transactions(String id) throws IOException, UnreadableWalletException {
        Wallet wallet = loadOrCreate(id);
        List<TxInfo> result = new ArrayList<>();
        for (Transaction tx : wallet.getTransactionsByTime()) {
            result.add(toTxInfo(tx, wallet));
        }
        return result;
    }

    /**
     * Возвращает неподтверждённые входящие транзакции
     * (глубина &lt; {@value #CONFIRMATION_THRESHOLD}).
     *
     * @param id идентификатор кошелька
     * @return список {@link TxInfo} с неподтверждёнными транзакциями
     * @throws IOException               если не удалось загрузить кошелёк
     * @throws UnreadableWalletException если файл кошелька не читается
     */
    public synchronized List<TxInfo> pendingTransactions(String id) throws IOException, UnreadableWalletException {
        Wallet wallet = loadOrCreate(id);
        List<TxInfo> result = new ArrayList<>();
        for (Transaction tx : wallet.getTransactionsByTime()) {
            if (!isConfirmed(tx) && tx.getValue(wallet).toSat() > 0) {
                result.add(toTxInfo(tx, wallet));
            }
        }
        return result;
    }

    // ─── Отправка и импорт ───────────────────────────────────────────

    /**
     * Создаёт и подписывает транзакцию отправки средств без трансляции в сеть.
     *
     * @param id        идентификатор кошелька-отправителя
     * @param toAddress адрес получателя в формате сети кошелька
     * @param amountSat сумма перевода в сатоши
     * @return {@link SendResult} с идентификатором, hex и комиссией
     * @throws IOException               если не удалось загрузить/сохранить кошелёк
     * @throws UnreadableWalletException если файл кошелька не читается
     * @throws IllegalArgumentException  если адрес некорректен, недостаточно средств,
     *                                   сумма ниже dust-порога или транзакция не проходит валидацию
     */
    public synchronized SendResult send(String id, String toAddress, long amountSat)
            throws IOException, UnreadableWalletException {
        Wallet wallet = loadOrCreate(id);
        Address destination = addressParser.parseAddress(toAddress);
        Context.getOrCreate();
        SendRequest request = SendRequest.to(destination, Coin.ofSat(amountSat));
        Transaction tx = executeSend(wallet, request);
        wallet.saveToFile(fileFor(id));
        Coin fee = tx.getFee();
        return new SendResult(
                tx.getTxId().toString(),
                HexFormat.of().formatHex(tx.bitcoinSerialize()),
                fee != null ? fee.toSat() : null);
    }

    /**
     * Импортирует существующую транзакцию в кошелёк (для тестирования в regtest).
     *
     * @param id    идентификатор кошелька
     * @param hexTx hex-представление транзакции
     * @param depth глубина подтверждений (0 — неподтверждённая)
     * @return {@link TxInfo} с данными импортированной транзакции
     * @throws IOException               если не удалось загрузить/сохранить кошелёк
     * @throws UnreadableWalletException если файл кошелька не читается
     * @throws IllegalArgumentException  если hex некорректен
     */
    public synchronized TxInfo importTransaction(String id, String hexTx, int depth)
            throws IOException, UnreadableWalletException {
        Wallet wallet = loadOrCreate(id);
        Context.getOrCreate();
        Transaction tx = deserializeTransaction(hexTx);
        setTransactionConfidence(tx, depth);
        wallet.addWalletTransaction(new WalletTransaction(
                depth > 0 ? WalletTransaction.Pool.UNSPENT : WalletTransaction.Pool.PENDING, tx));
        wallet.saveToFile(fileFor(id));
        return toTxInfo(tx, wallet);
    }

    // ─── Вспомогательные методы ──────────────────────────────────────

    /**
     * Выполняет {@link Wallet#sendCoinsOffline} с обработкой ошибок.
     *
     * @param wallet  кошелёк-отправитель
     * @param request запрос на отправку
     * @return подписанная транзакция
     * @throws IllegalArgumentException при нехватке средств, dust-пороге и др.
     */
    private Transaction executeSend(Wallet wallet, SendRequest request) {
        try {
            return wallet.sendCoinsOffline(request);
        } catch (InsufficientMoneyException e) {
            throw new IllegalArgumentException("Недостаточно средств на балансе кошелька");
        } catch (Wallet.DustySendRequested e) {
            throw new IllegalArgumentException("Сумма перевода слишком мала (ниже dust-порога для P2WPKH-адресов)");
        } catch (Wallet.CouldNotAdjustDownwards e) {
            throw new IllegalArgumentException("Не удалось уменьшить сумму перевода: " + e.getMessage());
        } catch (Wallet.ExceededMaxTransactionSize e) {
            throw new IllegalArgumentException("Транзакция превышает максимально допустимый размер: " + e.getMessage());
        }
    }

    /**
     * Десериализует транзакцию из hex-строки.
     *
     * @param hexTx hex-представление транзакции
     * @return объект транзакции
     * @throws IllegalArgumentException если hex некорректен
     */
    private Transaction deserializeTransaction(String hexTx) {
        byte[] raw;
        try {
            raw = HexFormat.of().parseHex(hexTx);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Некорректный hex транзакции: " + e.getMessage());
        }
        try {
            return Transaction.read(java.nio.ByteBuffer.wrap(raw));
        } catch (Exception e) {
            throw new IllegalArgumentException("Не удалось десериализовать транзакцию: " + e.getMessage());
        }
    }

    /**
     * Устанавливает подтверждение транзакции.
     *
     * @param tx   транзакция
     * @param depth глубина подтверждений (0 — PENDING, 1+ — BUILDING)
     */
    private void setTransactionConfidence(Transaction tx, int depth) {
        TransactionConfidence conf = tx.getConfidence();
        if (depth > 0) {
            conf.setConfidenceType(TransactionConfidence.ConfidenceType.BUILDING);
            conf.setDepthInBlocks(depth);
        } else {
            conf.setConfidenceType(TransactionConfidence.ConfidenceType.PENDING);
        }
    }

    /**
     * Проверяет, подтверждена ли транзакция (depth ≥ {@value #CONFIRMATION_THRESHOLD}).
     *
     * @param tx транзакция
     * @return {@code true}, если подтверждена
     */
    private boolean isConfirmed(Transaction tx) {
        return tx.getConfidence().getDepthInBlocks() >= CONFIRMATION_THRESHOLD;
    }

    /**
     * Преобразует транзакцию bitcoinj в {@link TxInfo}.
     *
     * @param tx     транзакция
     * @param wallet кошелёк (для вычисления значения)
     * @return DTO с данными транзакции
     */
    private TxInfo toTxInfo(Transaction tx, Wallet wallet) {
        List<OutputInfo> outputs = new ArrayList<>();
        for (TransactionOutput out : tx.getOutputs()) {
            Address to = out.getScriptPubKey().getToAddress(network);
            outputs.add(new OutputInfo(to.toString(), out.getValue().toSat()));
        }
        Coin fee = tx.getFee();
        return new TxInfo(
                tx.getTxId().toString(),
                tx.getValue(wallet).toSat(),
                fee != null ? fee.toSat() : null,
                outputs,
                tx.getConfidence().getDepthInBlocks());
    }
}
