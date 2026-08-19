package com.example.springmvcapp.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PartsCatalogController.class)
class PartsCatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void carParts_returnsListOfParts() throws Exception {
        mockMvc.perform(get("/api/car-parts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").isNotEmpty())
                .andExpect(jsonPath("$[0].description").isNotEmpty())
                .andExpect(jsonPath("$[0].price").isNumber())
                .andExpect(jsonPath("$[0].image").isNotEmpty());
    }
}