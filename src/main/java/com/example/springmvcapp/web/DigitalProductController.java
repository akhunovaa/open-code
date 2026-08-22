package com.example.springmvcapp.web;

import java.io.IOException;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.springmvcapp.service.DigitalProductService;

/**
 * REST-контроллер для цифровых продуктов.
 *
 * <p>Предоставляет API для:</p>
 * <ul>
 *   <li>{@code GET /api/products/agile-model} — информация о продукте;</li>
 *   <li>{@code POST /api/products/agile-model/order} — создание заказа (RSA-шифрование ссылки);</li>
 *   <li>{@code POST /api/products/agile-model/fulfill} — выдача ключа после оплаты.</li>
 * </ul>
 *
 * @see DigitalProductService
 */
@RestController
public class DigitalProductController {

    private final DigitalProductService productService;

    public DigitalProductController(DigitalProductService productService) {
        this.productService = productService;
    }

    /**
     * Возвращает информацию о продукте.
     *
     * @return карта с названием, описанием, ценой и обложкой
     */
    @GetMapping("/api/products/agile-model")
    public Map<String, Object> productInfo() {
        return productService.getProductInfo();
    }

    /**
     * Создаёт заказ: генерирует RSA-ключи и шифрует ссылку на файл.
     *
     * @param body тело запроса с полем {@code orderId}
     * @return зашифрованная ссылка и публичный ключ
     */
    @PostMapping("/api/products/agile-model/order")
    public Map<String, String> createOrder(@RequestBody OrderRequest body) {
        return productService.createOrder(body.getOrderId());
    }

    /**
     * Выдаёт приватный RSA-ключ после подтверждения оплаты.
     *
     * @param body тело запроса с полем {@code orderId}
     * @return приватный ключ для расшифровки ссылки
     */
    @PostMapping("/api/products/agile-model/fulfill")
    public Map<String, String> fulfillOrder(@RequestBody OrderRequest body) {
        return productService.fulfillOrder(body.getOrderId());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleError(Exception e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> handleIo(Exception e) {
        return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
    }

    /**
     * Тело запроса для операций с заказом.
     */
    public static class OrderRequest {
        private String orderId;

        public String getOrderId() { return orderId; }
        public void setOrderId(String orderId) { this.orderId = orderId; }
    }
}
