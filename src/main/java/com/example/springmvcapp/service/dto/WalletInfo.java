package com.example.springmvcapp.service.dto;

import java.util.List;

/**
 * Информация о кошельке для REST API.
 *
 * @param id               идентификатор кошелька
 * @param network          имя сети (например, {@code mainnet} или {@code regtest})
 * @param mnemonic         мнемоническая фраза BIP39
 * @param balanceSat       доступный баланс в сатоши
 * @param address          текущий receive-адрес (последний выданный)
 * @param addresses        список всех выданных адресов
 * @param transactionCount количество транзакций кошелька
 */
public record WalletInfo(
        String id,
        String network,
        String mnemonic,
        long balanceSat,
        String address,
        List<String> addresses,
        int transactionCount
) {
}
