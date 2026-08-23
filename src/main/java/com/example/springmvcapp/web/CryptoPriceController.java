package com.example.springmvcapp.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.springmvcapp.service.BtcPriceService;
import com.example.springmvcapp.service.dto.CachedPrice;

/**
 * REST-контроллер для получения курса биткоина.
 *
 * <p>Предоставляет HTTP API для:</p>
 * <ul>
 *     <li>{@code GET /api/btc-price} — запрос свежего курса у CoinGecko;</li>
 *     <li>{@code GET /api/btc-price/cached} — получение последнего курса
 *         без нового запроса к API.</li>
 * </ul>
 *
 * @see BtcPriceService
 */
@RestController
public class CryptoPriceController {

    private final BtcPriceService btcPriceService;

    public CryptoPriceController(BtcPriceService btcPriceService) {
        this.btcPriceService = btcPriceService;
    }

    /**
     * Запрашивает свежий курс биткоина у CoinGecko и возвращает результат.
     *
     * @return {@link CachedPrice} с ценами в USD/RUB и меткой времени
     */
    @GetMapping("/api/btc-price")
    public CachedPrice btcPrice() {
        return btcPriceService.refreshPrice();
    }

    /**
     * Возвращает последний полученный курс без нового запроса к API.
     *
     * @return {@link CachedPrice} со статусом {@code 200}, либо {@code 404},
     *         если курс ещё не запрашивался
     */
    @GetMapping("/api/btc-price/cached")
    public ResponseEntity<CachedPrice> btcPriceCached() {
        return btcPriceService.getCachedPrice()
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Обрабатывает непредвиденные ошибки сервиса.
     *
     * @param e перехваченное исключение
     * @return ответ со статусом {@code 500} и полем {@code error}
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleError(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
    }
}
