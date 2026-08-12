package com.example.springmvcapp.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = {HomeController.class, RoomController.class})
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void room_returnsViewWithName() throws Exception {
        mockMvc.perform(get("/room"))
                .andExpect(status().isOk())
                .andExpect(view().name("room"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("room.obj")));
    }
}
