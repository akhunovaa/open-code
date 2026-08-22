package com.example.springmvcapp.web;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.springmvcapp.service.BtcPriceService;
import com.example.springmvcapp.service.dto.CachedPrice;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CryptoPriceController.class)
class CryptoPriceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BtcPriceService btcPriceService;

    @Test
    void btcPrice_returnsPriceFromBinance() throws Exception {
        when(btcPriceService.refreshPrice()).thenReturn(
                new CachedPrice("64427.39000000", "5820000.00000000", "2026-08-20T19:04:41Z"));

        mockMvc.perform(get("/api/btc-price"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceUsd").value("64427.39000000"))
                .andExpect(jsonPath("$.priceRub").value("5820000.00000000"))
                .andExpect(jsonPath("$.savedAt").isNotEmpty());
    }

    @Test
    void btcPrice_returnsErrorTextWhenRequestFails() throws Exception {
        when(btcPriceService.refreshPrice()).thenReturn(
                new CachedPrice("ошибка получения цены", "ошибка получения цены", "2026-08-20T19:04:41Z"));

        mockMvc.perform(get("/api/btc-price"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceUsd").value("ошибка получения цены"))
                .andExpect(jsonPath("$.priceRub").value("ошибка получения цены"));
    }

    @Test
    void btcPriceCached_returnsCachedWhenExists() throws Exception {
        when(btcPriceService.getCachedPrice()).thenReturn(Optional.of(
                new CachedPrice("100000.00", "9000000.00", "2026-08-20T19:04:41Z")));

        mockMvc.perform(get("/api/btc-price/cached"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceUsd").value("100000.00"))
                .andExpect(jsonPath("$.priceRub").value("9000000.00"))
                .andExpect(jsonPath("$.savedAt").value("2026-08-20T19:04:41Z"));
    }

    @Test
    void btcPriceCached_returns404WhenNotCached() throws Exception {
        when(btcPriceService.getCachedPrice()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/btc-price/cached"))
                .andExpect(status().isNotFound());
    }
}
