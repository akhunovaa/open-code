package com.example.springmvcapp.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BmwPartsCatalogController {

    private static final String[][] CATALOG = {
            {"Тормозные колодки BMW", "Передние, оригинал, комплект 4 шт"},
            {"Тормозные диски BMW", "Вентилируемые, комплект 2 шт, для F10/E60"},
            {"Масляный фильтр BMW", "Оригинальный, с прокладкой и кольцом"},
            {"Воздушный фильтр BMW", "Для бензиновых двигателей N20/N47"},
            {"Свечи зажигания BMW", "Иридиевые, комплект 6 шт"},
            {"Ремень ГРМ BMW", "Комплект с роликами и натяжителем"},
            {"Цепь ГРМ BMW", "Усиленная, с успокоителем, N47/N57"},
            {"Радиатор охлаждения BMW", "Алюминиевый, двухрядный"},
            {"Амортизатор передний BMW", "Газомасляный, усиленный"},
            {"Пружина задняя BMW", "Усиленная, комплект 2 шт"},
            {"Аккумулятор BMW", "AGM 90 Ач, с гарантией 2 года"},
            {"Стартер BMW", "Оригинальный, обслуженный"},
            {"Генератор BMW", "150 А, новый, с регулятором"},
            {"Фара BMW Angel Eyes", "С LED и омывателем"},
            {"Решётка радиатора BMW", "Хромированная, с эмблемой"},
            {"Эмблема BMW", "Капотная, оригинал, хром"},
            {"Капот BMW", "Стальной, без коррозии"},
            {"Крыло переднее BMW", "С цветом кузова"},
            {"Бампер M", "Передний, для M-пакета"},
            {"Зеркало боковое BMW", "С обогревом и поворотником"},
            {"Рулевая рейка BMW", "С усилителем, б/у, отличное состояние"},
            {"Турбина BMW", "Для дизельных двигателей, проверенная"},
            {"Тормозная жидкость BMW", "DOT 4, 1 литр"},
            {"Двигатель BMW", "Б/у, проверенный, с гарантией"},
            {"Коробка передач BMW", "ZF 8HP, проверенная, без нареканий"}
    };

    private static final String[] COLORS = {"#00449b", "#003d80", "#1a5bb8", "#66b8ff", "#002e5c", "#00336b", "#0080d8"};

    private final Random random = new Random();

    @GetMapping("/api/bmw-parts")
    public List<Part> bmwParts() {
        int count = 6 + random.nextInt(4); // 6-9 items
        List<Part> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String[] entry = CATALOG[random.nextInt(CATALOG.length)];
            int price = 1000 + random.nextInt(80000);
            result.add(new Part(entry[0], entry[1], price, makeImage(entry[0])));
        }
        return result;
    }

    private String makeImage(String name) {
        String color = COLORS[random.nextInt(COLORS.length)];
        String emoji = switch (name) {
            case "Тормозные колодки BMW", "Тормозные диски BMW" -> "🛑";
            case "Масляный фильтр BMW", "Воздушный фильтр BMW" -> "🌀";
            case "Свечи зажигания BMW" -> "⚡";
            case "Ремень ГРМ BMW", "Цепь ГРМ BMW", "Коробка передач BMW" -> "⚙️";
            case "Радиатор охлаждения BMW" -> "❄️";
            case "Амортизатор передний BMW", "Пружина задняя BMW" -> "🫨";
            case "Аккумулятор BMW" -> "🔋";
            case "Стартер BMW", "Генератор BMW" -> "🔧";
            case "Фара BMW Angel Eyes" -> "💡";
            case "Решётка радиатора BMW" -> "🛞";
            case "Эмблема BMW" -> "🚘";
            case "Капот BMW", "Крыло переднее BMW", "Бампер M" -> "🚗";
            case "Зеркало боковое BMW" -> "🪞";
            case "Рулевая рейка BMW" -> "🔄";
            case "Турбина BMW" -> "💨";
            case "Тормозная жидкость BMW" -> "🧴";
            case "Двигатель BMW" -> "🛠️";
            default -> "🔧";
        };
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='120' height='120'>"
                + "<rect width='120' height='120' rx='10' fill='" + color + "'/>"
                + "<text x='60' y='60' font-size='56' text-anchor='middle' dominant-baseline='central'>"
                + emoji + "</text></svg>";
        String base64 = java.util.Base64.getEncoder().encodeToString(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "data:image/svg+xml;base64," + base64;
    }

    public static class Part {
        private final String name;
        private final String description;
        private final int price;
        private final String image;

        public Part(String name, String description, int price, String image) {
            this.name = name;
            this.description = description;
            this.price = price;
            this.image = image;
        }

        public String getName() { return name; }
        public String getDescription() { return description; }
        public int getPrice() { return price; }
        public String getImage() { return image; }
    }
}