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
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.UnreadableWalletException;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.Wallet.BalanceType;
import org.springframework.stereotype.Service;

/**
 * Сервис, инкапсулирующий работу с биткоин-кошельками через библиотеку bitcoinj.
 *
 * <p>Сервис управляет детерминированными (BIP32/BIP39) кошельками в сети
 * {@code regtest} с типом адресов {@code P2WPKH}. Каждый кошелёк сериализуется
 * в protobuf-файл формата bitcoinj и хранится в каталоге
 * {@value #STORAGE_DIR} (расширение {@code .wallet}).</p>
 *
 * <p>Загруженные кошельки кэшируются в памяти и автоматически сохраняются на диск
 * при изменениях (автосейв с задержкой 500 мс). Идентификатор кошелька равен
 * имени файла без расширения.</p>
 *
 * <p>Набор операций соответствует потребностям REST API:
 * создание кошелька, получение адресов и баланса, просмотр транзакций
 * и оффлайн-отправка средств (без трансляции в сеть Bitcoin).</p>
 *
 * @see <a href="https://bitcoinj.org/javadoc/0.17.1/">bitcoinj 0.17.1 javadoc</a>
 */
@Service
public class BitcoinWalletService {

    /**
     * Каталог хранения файлов кошельков.
     */
    public static final Path STORAGE_DIR = Paths.get("/Users/azatakhunov/temp/btc");

    /**
     * Сеть Bitcoin, в которой работают кошельки сервиса.
     */
    private static final BitcoinNetwork NETWORK = BitcoinNetwork.REGTEST;

    /**
     * Тип выходных скриптов (адресов), используемый для получаемых средств.
     */
    private static final ScriptType SCRIPT_TYPE = ScriptType.P2WPKH;

    /**
     * Кэш загруженных кошельков: идентификатор кошелька → экземпляр {@link Wallet}.
     */
    private final Map<String, Wallet> cache = new ConcurrentHashMap<>();

    /**
     * Парсер адресов для сети {@link #NETWORK}.
     */
    private final AddressParser addressParser = AddressParser.getDefault(NETWORK);

    /**
     * Возвращает файл кошелька по его идентификатору.
     *
     * @param id идентификатор кошелька
     * @return файл {@code <STORAGE_DIR>/<id>.wallet}
     */
    private synchronized File fileFor(String id) {
        return STORAGE_DIR.resolve(id + ".wallet").toFile();
    }

    /**
     * Загружает кошелёк из кэша или с диска, при необходимости создавая новый.
     *
     * <p>Если кошелёк уже есть в {@link #cache}, возвращается он. Иначе, если файл
     * {@code <id>.wallet} существует, кошелёк десериализуется из него; в противном
     * случае создаётся новый детерминированный кошелёк. После загрузки включается
     * автосейв и кошелёк помещается в кэш.</p>
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
        Wallet wallet = file.exists() ? Wallet.loadFromFile(file)
                : Wallet.createDeterministic(NETWORK, SCRIPT_TYPE);
        wallet.autosaveToFile(file, java.time.Duration.ofMillis(500), null);
        cache.put(id, wallet);
        return wallet;
    }

    /**
     * Создаёт новый кошелёк с уникальным идентификатором.
     *
     * <p>Создаётся UUID-идентификатор, каталог {@link #STORAGE_DIR} создаётся при
     * необходимости, кошелёк немедленно сохраняется на диск. При последующих
     * обращениях кошелёк восстанавливается из файла.</p>
     *
     * @return идентификатор созданного кошелька
     * @throws IOException               если не удалось создать каталог или сохранить файл
     * @throws UnreadableWalletException если созданный кошелёк не удалось загрузить
     */
    public String createWallet() throws IOException, UnreadableWalletException {
        Files.createDirectories(STORAGE_DIR);
        String id = UUID.randomUUID().toString();
        loadOrCreate(id).saveToFile(fileFor(id));
        return id;
    }

    /**
     * Возвращает идентификаторы всех сохранённых кошельков.
     *
     * <p>Сканирует {@link #STORAGE_DIR} и возвращает имена файлов с расширением
     * {@code .wallet} без расширения. Если каталога не существует, возвращается
     * пустой список.</p>
     *
     * @return список идентификаторов кошельков
     * @throws IOException если не удалось прочитать каталог хранения
     */
    public List<String> listWallets() throws IOException {
        if (!Files.isDirectory(STORAGE_DIR)) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        try (var stream = Files.list(STORAGE_DIR)) {
            stream.filter(p -> p.toString().endsWith(".wallet"))
                    .forEach(p -> ids.add(p.getFileName().toString().replace(".wallet", "")));
        }
        return ids;
    }

    /**
     * Возвращает полную информацию о кошельке.
     *
     * <p>Помимо идентификатора и сети возвращаются мнемоническая фраза (BIP39),
     * доступный баланс в сатоши, текущий receive-адрес, список всех выданных
     * адресов и количество транзакций. Вызов также выдаёт свежий адрес кошелька.</p>
     *
     * @param id идентификатор кошелька
     * @return {@link WalletInfo} с данными кошелька
     * @throws IOException               если не удалось загрузить кошелёк
     * @throws UnreadableWalletException если файл кошелька не читается
     */
    public WalletInfo getWallet(String id) throws IOException, UnreadableWalletException {
        Wallet wallet = loadOrCreate(id);
        String fresh = wallet.freshReceiveAddress().toString();
        List<String> addresses = wallet.getIssuedReceiveAddresses().stream()
                .map(Address::toString)
                .toList();
        return new WalletInfo(
                id,
                NETWORK.toString(),
                wallet.getKeyChainSeed() != null ? wallet.getKeyChainSeed().getMnemonicString() : null,
                wallet.getBalance(BalanceType.AVAILABLE).toSat(),
                fresh,
                addresses,
                wallet.getTransactionsByTime().size());
    }

    /**
     * Генерирует свежий receive-адрес кошелька.
     *
     * @param id идентификатор кошелька
     * @return строка с новым адресом в формате bech32
     * @throws IOException               если не удалось загрузить кошелёк
     * @throws UnreadableWalletException если файл кошелька не читается
     */
    public String freshAddress(String id) throws IOException, UnreadableWalletException {
        return loadOrCreate(id).freshReceiveAddress().toString();
    }

    /**
     * Возвращает доступный баланс кошелька в сатоши.
     *
     * @param id идентификатор кошелька
     * @return баланс в сатоши (тип {@link BalanceType#AVAILABLE})
     * @throws IOException               если не удалось загрузить кошелёк
     * @throws UnreadableWalletException если файл кошелька не читается
     */
    public long balance(String id) throws IOException, UnreadableWalletException {
        return loadOrCreate(id).getBalance(BalanceType.AVAILABLE).toSat();
    }

    /**
     * Возвращает транзакции кошелька, отсортированные по времени.
     *
     * <p>Для каждой транзакции формируется {@link TxInfo} с идентификатором,
     * изменением баланса, комиссией и списком выходов (адрес получателя + сумма).
     * Адрес выхода вычисляется из скрипта {@code scriptPubKey}.</p>
     *
     * @param id идентификатор кошелька
     * @return список {@link TxInfo} с транзакциями
     * @throws IOException               если не удалось загрузить кошелёк
     * @throws UnreadableWalletException если файл кошелька не читается
     */
    public List<TxInfo> transactions(String id) throws IOException, UnreadableWalletException {
        Wallet wallet = loadOrCreate(id);
        List<TxInfo> result = new ArrayList<>();
        for (Transaction tx : wallet.getTransactionsByTime()) {
            List<OutputInfo> outputs = new ArrayList<>();
            for (TransactionOutput out : tx.getOutputs()) {
                Address to = out.getScriptPubKey().getToAddress(NETWORK);
                outputs.add(new OutputInfo(to.toString(), out.getValue().toSat()));
            }
            Coin fee = tx.getFee();
            result.add(new TxInfo(
                    tx.getTxId().toString(),
                    tx.getValue(wallet).toSat(),
                    fee != null ? fee.toSat() : null,
                    outputs));
        }
        return result;
    }

    /**
     * Создаёт и подписывает транзакцию отправки средств без трансляции в сеть.
     *
     * <p>Адрес получателя парсится парсером {@link #addressParser}, после чего
     * формируется запрос {@link SendRequest#to} и выполняется
     * {@link Wallet#sendCoinsOffline}. Готовый кошелёк сохраняется на диск.
     * При нехватке средств выбрасывается {@link IllegalArgumentException}.</p>
     *
     * @param id        идентификатор кошелька-отправителя
     * @param toAddress адрес получателя в формате сети кошелька
     * @param amountSat сумма перевода в сатоши
     * @return {@link SendResult} с идентификатором транзакции, hex-представлением
     *         и комиссией
     * @throws IOException               если не удалось загрузить/сохранить кошелёк
     * @throws UnreadableWalletException если файл кошелька не читается
     * @throws IllegalArgumentException  если адрес некорректен, недостаточно средств,
     *                                   сумма ниже dust-порога или транзакция не проходит валидацию
     */
    public SendResult send(String id, String toAddress, long amountSat) throws IOException, UnreadableWalletException {
        Wallet wallet = loadOrCreate(id);
        Address destination = addressParser.parseAddress(toAddress);
        Context.getOrCreate();
        SendRequest request = SendRequest.to(destination, Coin.ofSat(amountSat));
        Transaction tx;
        try {
            tx = wallet.sendCoinsOffline(request);
        } catch (InsufficientMoneyException e) {
            throw new IllegalArgumentException("Недостаточно средств на балансе кошелька");
        } catch (Wallet.DustySendRequested e) {
            throw new IllegalArgumentException("Сумма перевода слишком мала (ниже dust-порога для P2WPKH-адресов)");
        } catch (Wallet.CouldNotAdjustDownwards e) {
            throw new IllegalArgumentException("Не удалось уменьшить сумму перевода: "
                    + e.getMessage());
        } catch (Wallet.ExceededMaxTransactionSize e) {
            throw new IllegalArgumentException("Транзакция превышает максимально допустимый размер: "
                    + e.getMessage());
        }
        wallet.saveToFile(fileFor(id));
        Coin fee = tx.getFee();
        return new SendResult(
                tx.getTxId().toString(),
                HexFormat.of().formatHex(tx.bitcoinSerialize()),
                fee != null ? fee.toSat() : null);
    }

    /**
     * DTO с информацией о кошельке.
     */
    public static class WalletInfo {
        private final String id;
        private final String network;
        private final String mnemonic;
        private final long balanceSat;
        private final String address;
        private final List<String> addresses;
        private final int transactionCount;

        /**
         * Создаёт DTO с данными кошелька.
         *
         * @param id               идентификатор кошелька
         * @param network          имя сети (например, {@code regtest})
         * @param mnemonic         мнемоническая фраза BIP39
         * @param balanceSat       доступный баланс в сатоши
         * @param address          текущий receive-адрес
         * @param addresses        список всех выданных адресов
         * @param transactionCount количество транзакций кошелька
         */
        public WalletInfo(String id, String network, String mnemonic, long balanceSat,
                          String address, List<String> addresses, int transactionCount) {
            this.id = id;
            this.network = network;
            this.mnemonic = mnemonic;
            this.balanceSat = balanceSat;
            this.address = address;
            this.addresses = addresses;
            this.transactionCount = transactionCount;
        }

        /**
         * @return идентификатор кошелька
         */
        public String getId() { return id; }

        /**
         * @return имя сети (например, {@code regtest})
         */
        public String getNetwork() { return network; }

        /**
         * @return мнемоническая фраза BIP39
         */
        public String getMnemonic() { return mnemonic; }

        /**
         * @return доступный баланс в сатоши
         */
        public long getBalanceSat() { return balanceSat; }

        /**
         * @return текущий receive-адрес
         */
        public String getAddress() { return address; }

        /**
         * @return список всех выданных адресов
         */
        public List<String> getAddresses() { return addresses; }

        /**
         * @return количество транзакций кошелька
         */
        public int getTransactionCount() { return transactionCount; }
    }

    /**
     * DTO с данными выхода транзакции.
     */
    public static class OutputInfo {
        private final String address;
        private final long valueSat;

        /**
         * Создаёт DTO с данными выхода.
         *
         * @param address  адрес получателя
         * @param valueSat сумма в сатоши
         */
        public OutputInfo(String address, long valueSat) {
            this.address = address;
            this.valueSat = valueSat;
        }

        /**
         * @return адрес получателя
         */
        public String getAddress() { return address; }

        /**
         * @return сумма в сатоши
         */
        public long getValueSat() { return valueSat; }
    }

    /**
     * DTO с информацией о транзакции.
     */
    public static class TxInfo {
        private final String txId;
        private final long valueSat;
        private final Long feeSat;
        private final List<OutputInfo> outputs;

        /**
         * Создаёт DTO с данными транзакции.
         *
         * @param txId     идентификатор транзакции
         * @param valueSat изменение баланса кошелька в сатоши
         * @param feeSat   комиссия в сатоши или {@code null}, если не определена
         * @param outputs  список выходов транзакции
         */
        public TxInfo(String txId, long valueSat, Long feeSat, List<OutputInfo> outputs) {
            this.txId = txId;
            this.valueSat = valueSat;
            this.feeSat = feeSat;
            this.outputs = outputs;
        }

        /**
         * @return идентификатор транзакции
         */
        public String getTxId() { return txId; }

        /**
         * @return изменение баланса кошелька в сатоши
         */
        public long getValueSat() { return valueSat; }

        /**
         * @return комиссия в сатоши или {@code null}, если не определена
         */
        public Long getFeeSat() { return feeSat; }

        /**
         * @return список выходов транзакции
         */
        public List<OutputInfo> getOutputs() { return outputs; }
    }

    /**
     * DTO с результатом отправки средств.
     */
    public static class SendResult {
        private final String txId;
        private final String hex;
        private final Long feeSat;

        /**
         * Создаёт DTO с результатом отправки.
         *
         * @param txId   идентификатор транзакции
         * @param hex    hex-представление сериализованной транзакции
         * @param feeSat комиссия в сатоши или {@code null}, если не определена
         */
        public SendResult(String txId, String hex, Long feeSat) {
            this.txId = txId;
            this.hex = hex;
            this.feeSat = feeSat;
        }

        /**
         * @return идентификатор транзакции
         */
        public String getTxId() { return txId; }

        /**
         * @return hex-представление сериализованной транзакции
         */
        public String getHex() { return hex; }

        /**
         * @return комиссия в сатоши или {@code null}, если не определена
         */
        public Long getFeeSat() { return feeSat; }
    }
}