package com.example.springmvcapp.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PartsCatalogController {

    private static final String[][] CATALOG = {
            {"Тормозные колодки", "Передние, керамические, комплект 4 шт"},
            {"Тормозные диски", "Вентилируемые, комплект 2 шт, сталь"},
            {"Свечи зажигания", "Иридиевые, комплект 4 шт"},
            {"Масляный фильтр", "Оригинальный, для бензиновых и дизельных"},
            {"Воздушный фильтр", "Панельный, высокая пропускная способность"},
            {"Салонный фильтр", "Угольный, защита от пыли и запахов"},
            {"Амортизатор передний", "Газомасляный, усиленный"},
            {"Пружина задняя", "Усиленная, комплект 2 шт"},
            {"Шаровая опора", "Для нижнего рычага, комплект 2 шт"},
            {"Рулевой наконечник", "Правая сторона, новый"},
            {"Ремень ГРМ", "Комплект с роликами и натяжителем"},
            {"Цепь ГРМ", "Усиленная, с успокоителем"},
            {"Помпа водяная", "С крыльчаткой, для большинства моделей"},
            {"Термостат", "82 градуса, зимний вариант"},
            {"Радиатор охлаждения", "Медный, двухрядный"},
            {"Аккумулятор", "60 Ач, с гарантией 2 года"},
            {"Стартер", "Восстановленный, обслуженный"},
            {"Генератор", "105 А, новый, с регулятором"},
            {"Фара левая", "С ксеноном и омывателем"},
            {"Щётки стеклоочистителя", "Бескаркасные, 60 см, комплект"},
            {"Колпак колесный", "Литой, 16 дюймов"},
            {"Зеркало боковое", "Правое, с обогревом"},
            {"Бампер передний", "С креплениями, пластик"},
            {"Рулевая рейка", "С усилителем, б/у, отличное состояние"},
            {"Глушитель", "Задний, нержавейка"}
    };

    private static final String[] COLORS = {"#cc4444", "#4488cc", "#44aa55", "#bb8844", "#8888cc", "#aa5555", "#55aa88"};

    private final Random random = new Random();

    @GetMapping("/api/car-parts")
    public List<Part> carParts() {
        int count = 6 + random.nextInt(4); // 6-9 items
        List<Part> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String[] entry = CATALOG[random.nextInt(CATALOG.length)];
            int price = 500 + random.nextInt(50000);
            result.add(new Part(entry[0], entry[1], price, makeImage(entry[0])));
        }
        return result;
    }

    private String makeImage(String name) {
        String color = COLORS[random.nextInt(COLORS.length)];
        String emoji = switch (name) {
            case "Тормозные колодки", "Тормозные диски" -> "🛑";
            case "Свечи зажигания" -> "⚡";
            case "Масляный фильтр", "Воздушный фильтр", "Салонный фильтр" -> "🌀";
            case "Амортизатор передний", "Пружина задняя" -> "🫨";
            case "Шаровая опора", "Рулевой наконечник" -> "🔩";
            case "Ремень ГРМ", "Цепь ГРМ" -> "⚙️";
            case "Помпа водяная", "Радиатор охлаждения" -> "❄️";
            case "Термостат" -> "🌡️";
            case "Аккумулятор" -> "🔋";
            case "Стартер", "Генератор" -> "🔧";
            case "Фара левая" -> "💡";
            case "Щётки стеклоочистителя" -> "🌧️";
            case "Колпак колесный" -> "🛞";
            case "Зеркало боковое" -> "🪞";
            case "Бампер передний" -> "🚗";
            case "Рулевая рейка" -> "🔄";
            case "Глушитель" -> "💨";
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