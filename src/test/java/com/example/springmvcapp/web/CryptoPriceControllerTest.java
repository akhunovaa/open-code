package com.example.springmvcapp.web;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CryptoPriceController.class)
class CryptoPriceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HttpClient httpClient;

    @Test
    void btcPrice_returnsPriceFromBinance() throws Exception {
        HttpResponse<String> usdResponse = mockResponse(200, "{\"symbol\":\"BTCUSDT\",\"price\":\"64427.39000000\"}");
        HttpResponse<String> rubResponse = mockResponse(200, "{\"symbol\":\"BTCRUB\",\"price\":\"5820000.00000000\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(usdResponse, rubResponse);

        mockMvc.perform(get("/api/btc-price"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceUsd").value("64427.39000000"))
                .andExpect(jsonPath("$.priceRub").value("5820000.00000000"));
    }

    @Test
    void btcPrice_returnsErrorTextWhenRequestFails() throws Exception {
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new java.io.IOException("down"));

        mockMvc.perform(get("/api/btc-price"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priceUsd").value("ошибка получения цены"))
                .andExpect(jsonPath("$.priceRub").value("ошибка получения цены"));
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> mockResponse(int statusCode, String body) {
        HttpResponse<String> response = org.mockito.Mockito.mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }
}