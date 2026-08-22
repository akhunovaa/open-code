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
import java.util.HashMap;
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
 * После оплаты покупатель получает расшифрованную ссылку для скачивания.</p>
 */
@Service
public class DigitalProductService {

    private static final String RSA_ALGORITHM = "RSA";
    private static final int RSA_KEY_SIZE = 2048;

    private final Path storageDir;
    private final Map<String, String[]> orderData = new ConcurrentHashMap<>();

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
        String filePath = storageDir.resolve("agile-model.html").toString();
        String encrypted = encrypt(filePath, keys.getPublic());
        orderData.put(orderId, new String[]{
                encrypted,
                encodeBase64(keys.getPublic().getEncoded()),
                encodeBase64(keys.getPrivate().getEncoded())
        });
        return Map.of(
                "orderId", orderId,
                "encryptedLink", encrypted,
                "publicKey", encodeBase64(keys.getPublic().getEncoded())
        );
    }

    /**
     * Выдаёт расшифрованную ссылку для скачивания после подтверждения оплаты.
     *
     * <p>Расшифровывает зашифрованную ссылку приватным RSA-ключом и
     * возвращает прямую ссылку для скачивания товара. После выдачи
     * данные заказа удаляются из памяти.</p>
     *
     * @param orderId идентификатор заказа
     * @return карта с расшифрованной ссылкой для скачивания
     * @throws IllegalArgumentException если заказ не найден
     */
    public Map<String, String> fulfillOrder(String orderId) {
        String[] data = orderData.get(orderId);
        if (data == null) {
            throw new IllegalArgumentException("Заказ не найден: " + orderId);
        }
        String encryptedLink = data[0];
        String privateKeyBase64 = data[2];
        orderData.remove(orderId);

        PrivateKey privateKey = decodePrivateKey(privateKeyBase64);
        String decryptedPath = decrypt(encryptedLink, privateKey);
        String fileName = Path.of(decryptedPath).getFileName().toString();

        Map<String, String> result = new HashMap<>();
        result.put("orderId", orderId);
        result.put("downloadUrl", "/api/products/agile-model/download/" + orderId);
        result.put("fileName", fileName);
        result.put("status", "paid");
        return result;
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

    private String decrypt(String encryptedBase64, PrivateKey privateKey) {
        try {
            Cipher cipher = Cipher.getInstance(RSA_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, privateKey);
            byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(encryptedBase64));
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка расшифровки: " + e.getMessage(), e);
        }
    }

    private PrivateKey decodePrivateKey(String base64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64);
            java.security.spec.PKCS8EncodedKeySpec spec = new java.security.spec.PKCS8EncodedKeySpec(keyBytes);
            return java.security.KeyFactory.getInstance(RSA_ALGORITHM).generatePrivate(spec);
        } catch (Exception e) {
            throw new RuntimeException("Ошибка декодирования приватного ключа: " + e.getMessage(), e);
        }
    }

    private String encodeBase64(byte[] data) {
        return Base64.getEncoder().encodeToString(data);
    }
}
