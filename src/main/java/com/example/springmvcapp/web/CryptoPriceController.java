package com.example.springmvcapp.web;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
public class CryptoPriceController {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CryptoPriceController(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @GetMapping("/api/btc-price")
    public BtcPrice btcPrice() {
        return new BtcPrice(fetchPrice("BTCUSDT"), fetchPrice("BTCRUB"));
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

    public static class BtcPrice {
        private final String priceUsd;
        private final String priceRub;

        public BtcPrice(String priceUsd, String priceRub) {
            this.priceUsd = priceUsd;
            this.priceRub = priceRub;
        }

        public String getPriceUsd() {
            return priceUsd;
        }

        public String getPriceRub() {
            return priceRub;
        }

        public String getPrice() {
            return priceUsd;
        }
    }
}