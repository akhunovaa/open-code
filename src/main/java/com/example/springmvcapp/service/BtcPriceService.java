package com.example.springmvcapp.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.springmvcapp.service.dto.CachedPrice;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Сервис для получения и кэширования курса биткоина.
 *
 * <p>Свежий курс запрашивается у Binance API (BTCUSDT, BTCRUB) и сохраняется
 * в файл {@code btc-price.json} в каталоге хранения с меткой времени.
 * Кэш можно прочитать без запроса к бирже через {@link #getCachedPrice()}.</p>
 *
 * <p>Все методы синхронизированы для защиты от гонок при параллельной записи
 * файла кэша.</p>
 *
 * @see CachedPrice
 */
@Service
public class BtcPriceService {

    private static final String BINANCE_API = "https://api.binance.com/api/v3/ticker/price?symbol=";
    private static final String PRICE_ERROR = "ошибка получения цены";

    private final Path storageDir;
    private final Path priceFile;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Создаёт сервис курса биткоина.
     *
     * @param httpClient HTTP-клиент для запросов к Binance
     * @param storageDir каталог хранения файла кэша (свойство {@code btc.storage-dir})
     */
    public BtcPriceService(HttpClient httpClient,
                           @Value("${btc.storage-dir:/data}") String storageDir) {
        this.httpClient = httpClient;
        this.storageDir = Paths.get(storageDir);
        this.priceFile = this.storageDir.resolve("btc-price.json");
    }

    /**
     * Возвращает сохранённый курс, если файл кэша существует.
     *
     * @return {@link Optional} с {@link CachedPrice} или пустой, если кэша нет
     */
    public synchronized Optional<CachedPrice> getCachedPrice() {
        if (!Files.exists(priceFile)) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(Files.readString(priceFile));
            String priceUsd = node.path("priceUsd").asText();
            String priceRub = node.path("priceRub").asText();
            String savedAt = node.path("savedAt").asText();
            if (priceUsd.isEmpty() && priceRub.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new CachedPrice(priceUsd, priceRub, savedAt));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Запрашивает свежий курс у Binance, сохраняет в кэш и возвращает.
     *
     * @return {@link CachedPrice} с актуальными ценами и меткой времени
     */
    public synchronized CachedPrice refreshPrice() {
        String priceUsd = fetchPrice("BTCUSDT");
        String priceRub = fetchPrice("BTCRUB");
        String savedAt = Instant.now().toString();
        CachedPrice result = new CachedPrice(priceUsd, priceRub, savedAt);
        savePrice(result);
        return result;
    }

    /**
     * Сохраняет курс в файл кэша.
     *
     * @param price данные курса для сохранения
     */
    private synchronized void savePrice(CachedPrice price) {
        try {
            Files.createDirectories(storageDir);
            ObjectNode node = objectMapper.createObjectNode();
            node.put("priceUsd", price.priceUsd());
            node.put("priceRub", price.priceRub());
            node.put("savedAt", price.savedAt());
            Files.writeString(priceFile, objectMapper.writeValueAsString(node));
        } catch (IOException e) {
            // кэш не критичен — игнорируем ошибку записи
        }
    }

    /**
     * Запрашивает цену у Binance API по символу (BTCUSDT, BTCRUB).
     *
     * @param symbol торговый символ Binance
     * @return цена строкой или сообщение об ошибке
     */
    private String fetchPrice(String symbol) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BINANCE_API + symbol))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode node = objectMapper.readTree(response.body());
                JsonNode price = node.get("price");
                if (price != null && price.isTextual()) {
                    return price.asText();
                }
            }
        } catch (Exception e) {
            // fall through to error response
        }
        return PRICE_ERROR;
    }
}
