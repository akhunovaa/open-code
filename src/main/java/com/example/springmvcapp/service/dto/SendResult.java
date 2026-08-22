package com.example.springmvcapp.service.dto;

/**
 * Результат отправки средств из кошелька.
 *
 * @param txId   идентификатор подписанной транзакции
 * @param hex    hex-представление сериализованной транзакции
 * @param feeSat комиссия в сатоши или {@code null}, если не определена
 */
public record SendResult(
        String txId,
        String hex,
        Long feeSat
) {
}
