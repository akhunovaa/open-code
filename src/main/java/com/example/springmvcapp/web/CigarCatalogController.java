package com.example.springmvcapp.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CigarCatalogController {

    private static final String[][] CATALOG = {
            {"Cohiba Behike BHK 52", "Редчайшая лимитированная серия. Тонкие ароматы какао, кедра и кофе, бархатистый дым."},
            {"Cohiba Siglo VI", "Флагманская витола. Крепкая, но элегантная, с нотами специй и сливочной древесины."},
            {"Montecristo No. 2", "Легендарная пирамида. Сбалансированная, с ароматами орехов, дуба и лёгкой сладости."},
            {"Partagás Serie D No. 4", "Классическая робаста. Землистая, пряная, насыщенная, с долгим послевкусием."},
            {"Romeo y Julieta Churchill", "Аристократичная и мягкая. Кремовые ноты, лёгкая пряность, идеальна для вечера."},
            {"H. Upmann Magnum 50", "Сбалансированная классика. Цитрусовые и древесные оттенки, деликатный дым."},
            {"Trinidad Fundadores", "Тонкая и длинная витола. Изысканная, с нотами меда, трав и белого перца."},
            {"Bolivar Belicoso Fino", "Пряная и мощная. Землистая база с нотками какао и гвоздики, для ценителей."},
            {"Hoyo de Monterrey Epicure No. 2", "Лёгкая и сливочная. Мягкие табачные ароматы с оттенком сливочного масла."},
            {"Punch Punch", "Яркая и насыщенная. Специи, орехи и дуб, уверенный характер."}
    };

    private static final String[] COLORS = {"#7a4a1a", "#6b3d12", "#8a5a2b", "#5d3410", "#9a6a3a", "#6e4420", "#8b5e34", "#74451a"};

    private final Random random = new Random();

    @GetMapping("/api/cigars")
    public List<Cigar> cigars() {
        int count = 6 + random.nextInt(3); // 6-8 items
        List<Cigar> result = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String[] entry = CATALOG[random.nextInt(CATALOG.length)];
            int price = 1200 + random.nextInt(15000);
            result.add(new Cigar(entry[0], entry[1], price, makeImage(entry[0])));
        }
        return result;
    }

    private String makeImage(String name) {
        String color = COLORS[random.nextInt(COLORS.length)];
        String label = "CUBA";
        String svg = "<svg xmlns='http://www.w3.org/2000/svg' width='120' height='120'>"
                + "<rect width='120' height='120' rx='10' fill='" + color + "'/>"
                + "<rect x='10' y='42' width='100' height='28' rx='8' fill='#c98d4a'/>"
                + "<rect x='62' y='42' width='48' height='28' rx='4' fill='#f0dcb0'/>"
                + "<rect x='62' y='42' width='48' height='8' fill='#b03a2e'/>"
                + "<text x='60' y='90' font-size='14' text-anchor='middle' fill='#f0dcb0' font-weight='bold'>"
                + label + "</text>"
                + "<text x='60' y='108' font-size='10' text-anchor='middle' fill='#f0dcb0'>" + name + "</text>"
                + "</svg>";
        String base64 = java.util.Base64.getEncoder().encodeToString(svg.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "data:image/svg+xml;base64," + base64;
    }

    public static class Cigar {
        private final String name;
        private final String description;
        private final int price;
        private final String image;

        public Cigar(String name, String description, int price, String image) {
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