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
 * REST-контроллер для получения и кэширования курса биткоина.
 *
 * <p>Предоставляет HTTP API для:</p>
 * <ul>
 *     <li>{@code GET /api/btc-price} — запрос свежего курса у Binance и сохранение
 *         в {@code btc-price.json} в каталоге хранения;</li>
 *     <li>{@code GET /api/btc-price/cached} — получение последнего сохранённого
 *         курса без обращения к бирже.</li>
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
     * Запрашивает свежий курс биткоина у Binance, сохраняет его в файл кэша
     * и возвращает результат с меткой времени.
     *
     * @return {@link CachedPrice} с ценами в USD/RUB и временем сохранения
     */
    @GetMapping("/api/btc-price")
    public CachedPrice btcPrice() {
        return btcPriceService.refreshPrice();
    }

    /**
     * Возвращает последний сохранённый курс биткоина без запроса к бирже.
     *
     * @return {@link CachedPrice} со статусом {@code 200}, либо {@code 404},
     *         если кэш ещё не создан
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
