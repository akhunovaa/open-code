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

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Сервис для получения и кэширования курса биткоина.
 *
 * <p>Свежий курс запрашивается у Binance API (BTCUSDT, BTCRUB) и сохраняется
 * в файл {@code /Users/azatakhunov/temp/btc/btc-price.json} вместе с меткой
 * времени сохранения. При последующих обращениях можно получить сохранённый
 * курс без нового запроса к бирже через {@link #getCachedPrice()}.</p>
 */
@Service
public class BtcPriceService {

    /** Каталог хранения файла с кэшем курса (совпадает с каталогом кошельков). */
    public static final Path STORAGE_DIR = Paths.get("/Users/azatakhunov/temp/btc");

    private static final Path PRICE_FILE = STORAGE_DIR.resolve("btc-price.json");

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BtcPriceService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Возвращает сохранённый курс биткоина, если файл кэша существует.
     *
     * @return {@link Optional} с {@link CachedPrice} или пустой, если кэша нет
     */
    public Optional<CachedPrice> getCachedPrice() {
        if (!Files.exists(PRICE_FILE)) {
            return Optional.empty();
        }
        try {
            JsonNode node = objectMapper.readTree(Files.readString(PRICE_FILE));
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
     * Запрашивает свежий курс биткоина у Binance, сохраняет его в файл кэша
     * (перезаписывая старые данные) и возвращает результат.
     *
     * @return {@link CachedPrice} с актуальными ценами и меткой времени
     */
    public CachedPrice refreshPrice() {
        String priceUsd = fetchPrice("BTCUSDT");
        String priceRub = fetchPrice("BTCRUB");
        String savedAt = Instant.now().toString();
        CachedPrice result = new CachedPrice(priceUsd, priceRub, savedAt);
        savePrice(result);
        return result;
    }

    private void savePrice(CachedPrice price) {
        try {
            Files.createDirectories(STORAGE_DIR);
            ObjectNode node = objectMapper.createObjectNode();
            node.put("priceUsd", price.getPriceUsd());
            node.put("priceRub", price.getPriceRub());
            node.put("savedAt", price.getSavedAt());
            Files.writeString(PRICE_FILE, objectMapper.writeValueAsString(node));
        } catch (IOException e) {
            // кэш не критичен — игнорируем ошибку записи
        }
    }

    private String fetchPrice(String symbol) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.binance.com/api/v3/ticker/price?symbol=" + symbol))
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
        return "ошибка получения цены";
    }

    /**
     * DTO с кэшированным курсом биткоина.
     */
    public static class CachedPrice {
        private final String priceUsd;
        private final String priceRub;
        private final String savedAt;

        public CachedPrice(String priceUsd, String priceRub, String savedAt) {
            this.priceUsd = priceUsd;
            this.priceRub = priceRub;
            this.savedAt = savedAt;
        }

        public String getPriceUsd() { return priceUsd; }

        public String getPriceRub() { return priceRub; }

        public String getSavedAt() { return savedAt; }
    }
}
