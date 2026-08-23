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
 * Сервис для получения курса биткоина.
 *
 * <p>Курс запрашивается у CoinGecko API. Результат кэшируется в файл
 * {@code btc-price.json} — если CoinGecko недоступен, возвращается
 * последний сохранённый курс.</p>
 *
 * @see CachedPrice
 */
@Service
public class BtcPriceService {

    private static final String COINGECKO_API = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin&vs_currencies=usd,rub";
    private static final String PRICE_ERROR = "ошибка получения цены";

    private final Path storageDir;
    private final Path priceFile;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BtcPriceService(HttpClient httpClient,
                           @Value("${btc.storage-dir:/data}") String storageDir) {
        this.httpClient = httpClient;
        this.storageDir = Paths.get(storageDir);
        this.priceFile = this.storageDir.resolve("btc-price.json");
    }

    /**
     * Возвращает последний сохранённый курс, если файл кэша существует.
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
     * Запрашивает свежий курс у CoinGecko. Если запрос не удался —
     * возвращает последний кэшированный курс.
     *
     * @return {@link CachedPrice} с актуальными или кэшированными ценами
     */
    public synchronized CachedPrice refreshPrice() {
        String priceUsd = fetchCoinGeckoPrice("usd");
        String priceRub = fetchCoinGeckoPrice("rub");

        if (priceUsd != null && priceRub != null) {
            CachedPrice result = new CachedPrice(priceUsd, priceRub, Instant.now().toString());
            savePrice(result);
            return result;
        }

        Optional<CachedPrice> cached = getCachedPrice();
        if (cached.isPresent()) {
            return cached.get();
        }

        return new CachedPrice(PRICE_ERROR, PRICE_ERROR, Instant.now().toString());
    }

    /**
     * Сохраняет курс в файл кэша.
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
            // кэш не критичен
        }
    }

    /**
     * Запрашивает цену у CoinGecko API.
     *
     * @param currency валюта: {@code usd} или {@code rub}
     * @return цена строкой или {@code null} при ошибке
     */
    private String fetchCoinGeckoPrice(String currency) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(COINGECKO_API))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .header("User-Agent", "spring-mvc-app/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode btc = root.path("bitcoin");
                JsonNode priceNode = btc.path(currency);
                if (priceNode.isNumber()) {
                    return priceNode.asText();
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}
