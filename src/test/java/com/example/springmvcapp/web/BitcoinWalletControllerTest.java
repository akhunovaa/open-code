package com.example.springmvcapp.web;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.springmvcapp.service.BitcoinWalletService;
import com.example.springmvcapp.service.BitcoinWalletService.SendResult;
import com.example.springmvcapp.service.BitcoinWalletService.TxInfo;
import com.example.springmvcapp.service.BitcoinWalletService.WalletInfo;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BitcoinWalletController.class)
class BitcoinWalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BitcoinWalletService walletService;

    @Test
    void create_returnsWalletInfo() throws Exception {
        when(walletService.createWallet()).thenReturn("wallet-1");
        when(walletService.getWallet("wallet-1")).thenReturn(new WalletInfo(
                "wallet-1", "regtest", "abandon abandon", 0L, "bcrt1qtest",
                List.of("bcrt1qtest"), 0));

        mockMvc.perform(post("/api/btc/wallet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("wallet-1"))
                .andExpect(jsonPath("$.network").value("regtest"))
                .andExpect(jsonPath("$.mnemonic").isNotEmpty())
                .andExpect(jsonPath("$.balanceSat").isNumber())
                .andExpect(jsonPath("$.address").isNotEmpty());
    }

    @Test
    void balance_returnsSatoshis() throws Exception {
        when(walletService.balance("wallet-1")).thenReturn(123456L);

        mockMvc.perform(get("/api/btc/wallet/wallet-1/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balanceSat").value(123456));
    }

    @Test
    void freshAddress_returnsAddress() throws Exception {
        when(walletService.freshAddress("wallet-1")).thenReturn("bcrt1qnew");

        mockMvc.perform(post("/api/btc/wallet/wallet-1/address"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address").value("bcrt1qnew"));
    }

    @Test
    void send_returnsTxResult() throws Exception {
        when(walletService.send(anyString(), anyString(), anyLong()))
                .thenReturn(new SendResult("txid123", "hexdeadbeef", 1000L));

        mockMvc.perform(post("/api/btc/wallet/wallet-1/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"bcrt1qtest\",\"amountSat\":100000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.txId").value("txid123"))
                .andExpect(jsonPath("$.hex").value("hexdeadbeef"))
                .andExpect(jsonPath("$.feeSat").value(1000));
    }

    @Test
    void send_insufficientFunds_returns400() throws Exception {
        when(walletService.send(anyString(), anyString(), anyLong()))
                .thenThrow(new IllegalArgumentException("Недостаточно средств на балансе кошелька"));

        mockMvc.perform(post("/api/btc/wallet/wallet-1/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"bcrt1qtest\",\"amountSat\":100000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void send_dustAmount_returns400() throws Exception {
        when(walletService.send(anyString(), anyString(), anyLong()))
                .thenThrow(new IllegalArgumentException(
                        "Сумма перевода слишком мала (ниже dust-порога для P2WPKH-адресов)"));

        mockMvc.perform(post("/api/btc/wallet/wallet-1/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"to\":\"bcrt1qtest\",\"amountSat\":100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Сумма перевода слишком мала (ниже dust-порога для P2WPKH-адресов)"));
    }

    @Test
    void transactions_returnsList() throws Exception {
        when(walletService.transactions("wallet-1"))
                .thenReturn(List.of(new TxInfo("tx1", 5000L, 1000L, List.of())));

        mockMvc.perform(get("/api/btc/wallet/wallet-1/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].txId").value("tx1"))
                .andExpect(jsonPath("$[0].valueSat").value(5000));
    }
}