package com.example.springmvcapp.service;

import java.io.File;
import java.nio.file.Path;

import org.bitcoinj.base.BitcoinNetwork;
import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.params.BitcoinNetworkParams;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PreDestroy;

/**
 * Менеджер P2P-подключения к Bitcoin-сети для mainnet.
 *
 * <p>Инкапсулирует создание и управление {@link PeerGroup}, {@link BlockChain}
 * и {@link SPVBlockStore}. Запускается только для mainnet — позволяет кошелькам
 * автоматически обнаруживать входящие транзакции и обновлять подтверждения.
 * Для regtest/testnet/signet экземпляр создаётся, но не активируется
 * ({@link #isActive()} возвращает {@code false}).</p>
 *
 * <p>Не является Spring-бином — создаётся вручную в
 * {@link BitcoinWalletService#BitcoinWalletService}.</p>
 *
 * <p><b>SOLID — Single Responsibility:</b> класс отвечает только за
 * жизненный цикл P2P-подключения, а не за логику кошельков.</p>
 */
public class BtcP2pManager {

    private static final Logger log = LoggerFactory.getLogger(BtcP2pManager.class);

    private final BitcoinNetwork network;
    private final Path storageDir;
    private PeerGroup peerGroup;

    /**
     * Создаёт менеджер P2P.
     *
     * @param network    сеть Bitcoin
     * @param storageDir каталог для хранения SPV-файла блокчейна
     */
    public BtcP2pManager(BitcoinNetwork network, Path storageDir) {
        this.network = network;
        this.storageDir = storageDir;
    }

    /**
     * Запускает P2P-подключение, если сеть — mainnet.
     *
     * <p>Создаёт {@link SPVBlockStore} (файл {@code spv-blockchain.store}),
     * {@link BlockChain} и {@link PeerGroup} с DNS-discovery, затем
     * асинхронно подключается к пирам Bitcoin-сети.</p>
     */
    public void start() {
        if (network != BitcoinNetwork.MAINNET) {
            log.info("P2P не запускается: сеть {} (только mainnet)", network);
            return;
        }
        try {
            Context.getOrCreate();
            File chainFile = storageDir.resolve("spv-blockchain.store").toFile();
            BitcoinNetworkParams params = BitcoinNetworkParams.of(network);
            SPVBlockStore blockStore = new SPVBlockStore(params, chainFile);
            BlockChain chain = new BlockChain(network, blockStore);
            peerGroup = new PeerGroup(network, chain);
            peerGroup.addPeerDiscovery(new DnsDiscovery(network));
            peerGroup.startAsync();
            peerGroup.start();
            peerGroup.startBlockChainDownload(null);
            log.info("P2P запущен для mainnet: SPV-синхронизация началась");
        } catch (Exception e) {
            log.warn("P2P не запустился: {}", e.getMessage(), e);
            peerGroup = null;
        }
    }

    /**
     * Регистрирует кошелёк в P2P-сети для автоматического отслеживания транзакций.
     *
     * @param wallet кошелёк для регистрации
     */
    public void addWallet(Wallet wallet) {
        if (peerGroup != null) {
            peerGroup.addWallet(wallet);
            log.info("Кошелёк зарегистрирован в P2P-сети");
        }
    }

    /**
     * @return {@code true}, если P2P-подключение активно (mainnet)
     */
    public boolean isActive() {
        return peerGroup != null;
    }

    /**
     * Останавливает P2P-подключение при завершении приложения.
     */
    @PreDestroy
    public void shutdown() {
        if (peerGroup != null) {
            peerGroup.stop();
        }
    }
}
