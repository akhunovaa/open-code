package com.example.springmvcapp.service.dto;

/**
 * Данные выхода транзакции.
 *
 * @param address  адрес получателя
 * @param valueSat сумма в сатоши
 */
public record OutputInfo(
        String address,
        long valueSat
) {
}
