package com.example.springmvcapp.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.crypto.Cipher;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Сервис шифрования и доставки цифровых продуктов.
 *
 * <p>Использует RSA-шифрование: документ хранится в открытом виде в
 * {@code storageDir}, но ссылка на него выдаётся в зашифрованном виде.
 * После оплаты покупатель получает зашифрованную ссылку и RSA-ключ
 * для её расшифровки.</p>
 */
@Service
public class DigitalProductService {

    private static final String RSA_ALGORITHM = "RSA";
    private static final int RSA_KEY_SIZE = 2048;

    private final Path storageDir;
    private final Map<String, KeyPair> keyPairs = new ConcurrentHashMap<>();

    public DigitalProductService(@Value("${btc.storage-dir:/data}") String storageDir) {
        this.storageDir = Paths.get(storageDir);
    }

    /**
     * Возвращает информацию о продукте.
     *
     * @return карта с названием, описанием, ценой в рублях и путём к обложке
     */
    public Map<String, Object> getProductInfo() {
        return Map.of(
                "name", "Термодинамика Agile: Биофизическая модель спринт-планирования",
                "author", "Ахунов А.А.",
                "description", "Авторская методология управления Agile-спринтами, основанная на "
                        + "энергопотреблении мозга, натуральном логарифме и удельном импульсе усталости. "
                        + "18 слайдов с формулами, таблицами и визуализацией.",
                "priceRub", 3900,
                "coverUrl", "/agile-book/cover.svg"
        );
    }

    /**
     * Создаёт заказ: генерирует RSA-ключи, шифрует путь к файлу продукта.
     *
     * <p>Возвращает зашифрованную ссылку (Base64) и публичный ключ.
     * Приватный ключ хранится в памяти до подтверждения оплаты.</p>
     *
     * @param orderId идентификатор заказа (userId или UUID)
     * @return карта с зашифрованной ссылкой и публичным ключом
     */
    public Map<String, String> createOrder(String orderId) {
        KeyPair keys = generateKeyPair();
        keyPairs.put(orderId, keys);
        String filePath = storageDir.resolve("agile-model.html").toString();
        String encrypted = encrypt(filePath, keys.getPublic());
        return Map.of(
                "orderId", orderId,
                "encryptedLink", encrypted,
                "publicKey", encodeBase64(keys.getPublic().getEncoded())
        );
    }

    /**
     * Выдаёт приватный RSA-ключ после подтверждения оплаты.
     *
     * <p>Ключ позволяет расшифровать ссылку и получить путь к файлу.
     * После выдачи ключ удаляется из памяти.</p>
     *
     * @param orderId идентификатор заказа
     * @return карта с приватным ключом и ссылкой на зашифрованные данные
     * @throws IllegalArgumentException если заказ не найден
     */
    public Map<String, String> fulfillOrder(String orderId) {
        KeyPair keys = keyPairs.get(orderId);
        if (keys == null) {
            throw new IllegalArgumentException("Заказ не найден: " + orderId);
        }
        String privateKey = encodeBase64(keys.getPrivate().getEncoded());
        keyPairs.remove(orderId);
        return Map.of(
                "orderId", orderId,
                "privateKey", privateKey,
                "hint", "Используйте приватный ключ для расшифровки зашифрованной ссылки"
        );
    }

    /**
     * Читает содержимое файла продукта.
     *
     * @return содержимое файла в виде строки
     * @throws java.io.IOException если файл не найден
     */
    public String readProductContent() throws java.io.IOException {
        return Files.readString(storageDir.resolve("agile-model.html"), StandardCharsets.UTF_8);
    }

    private KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(RSA_ALGORITHM);
            generator.initialize(RSA_KEY_SIZE);
            return generator.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException("Не удалось сгенерировать RSA-ключи: " + e.getMessage(), e);
        }
    }

    private String encrypt(String data, PublicKey publicKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(encrypted);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка шифрования: " + e.getMessage(), e);
        }
    }

    private String encodeBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }
}
