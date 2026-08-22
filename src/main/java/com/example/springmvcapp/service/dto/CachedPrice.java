package com.example.springmvcapp.service.dto;

/**
 * Кэшированный курс биткоина.
 *
 * @param priceUsd цена в USD (строка из Binance API)
 * @param priceRub цена в RUB (строка из Binance API)
 * @param savedAt  метка времени сохранения в ISO-8601
 */
public record CachedPrice(
        String priceUsd,
        String priceRub,
        String savedAt
) {
}
