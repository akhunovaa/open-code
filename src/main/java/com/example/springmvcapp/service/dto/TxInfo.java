package com.example.springmvcapp.service.dto;

import java.util.List;

/**
 * Информация о транзакции кошелька.
 *
 * @param txId     идентификатор транзакции
 * @param valueSat изменение баланса кошелька в сатоши (положительное — входящая)
 * @param feeSat   комиссия в сатоши или {@code null}, если не определена
 * @param outputs  список выходов транзакции
 * @param depth     глубина подтверждений в блоках (0 — неподтверждённая)
 */
public record TxInfo(
        String txId,
        long valueSat,
        Long feeSat,
        List<OutputInfo> outputs,
        int depth
) {
}
