package com.bankapplication;

import com.bankapplication.management.controllers.BankAccountController;
import com.bankapplication.management.dto.CreateBankAccRequest;
import com.bankapplication.management.service.BankAccountService;
import com.bankapplication.management.service.TransferService;
import com.bankapplication.management.dto.TransferRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;

// tests for BankAccountController functionalities
class BankAccountControllerTest {

    private BankAccountService bankAccountService;
    private TransferService transferService;
    private BankAccountController controller;

    @BeforeEach
    void setUp() {
        bankAccountService = Mockito.mock(BankAccountService.class);
        transferService = Mockito.mock(TransferService.class);
        controller = new BankAccountController(bankAccountService, transferService);
    }

    @Test
    void createBankAccount_returnsSuccessMessage() {
        CreateBankAccRequest request = new CreateBankAccRequest();
        request.setAccountName("Savings");
        request.setCurrency("USD");
        request.setBalance(1000.0);
        request.setUserId(1L);

        Mockito.doNothing().when(bankAccountService).createBankAccount(any(CreateBankAccRequest.class));

        ResponseEntity<?> response = controller.createBankAccount(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("message", "Bank account created successfully"), response.getBody());
    }

    @Test
    void getUserBankAccounts_returnsList() {
        Long userId = 1L;
        ResponseEntity<?> response = controller.getUserBankAccounts(userId);
        assertEquals(200, response.getStatusCode().value());
    }

    @Test
    void deleteBankAccount_returnsNoContent() {
        Long userId = 1L;
        Long accountId = 10L;
        Mockito.doNothing().when(bankAccountService).deleteBankAccount(userId, accountId);

        ResponseEntity<?> response = controller.deleteBankAccount(userId, accountId);

        assertEquals(204, response.getStatusCode().value()); // No Content
    }

    @Test
    void getTotalAmount_returnsAmountMap() {
        Long userId = 1L;
        Double total = 1500.0;
        Mockito.when(bankAccountService.getTotalAmount(userId)).thenReturn(total);

        ResponseEntity<?> response = controller.getTotalAmount(userId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("totalAmount", total), response.getBody());
    }

    @Test
    void generateTransfer_returnsSuccessMessage() {
        TransferRequest request = new TransferRequest();
        request.setSenderAccountId(1L);
        request.setRecipientAccountNumber("123456");
        request.setAmount(100.0);
        request.setDescription("Test transfer");
        // parse date as LocalDate then convert to start of day
        request.setDate(LocalDate.parse("2026-01-07").atStartOfDay());

        // use matcher for LocalDateTime instead of parsing inside matcher
        Mockito.doNothing().when(transferService).transferFunds(
                anyLong(), anyString(), anyDouble(), anyString(), any(LocalDateTime.class)
        );

        ResponseEntity<?> response = controller.generateTransfer(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("message", "Transfer successful"), response.getBody());
    }

    @Test
    void getUserTransfers_returnsList() {
        Long userId = 1L;
        List<String> mockTransfers = List.of("Transfer1", "Transfer2");
        // stub the service to return the mock list
        Mockito.when(transferService.getUserTransfers(userId)).thenReturn((List)mockTransfers);

        ResponseEntity<?> response = controller.getUserTransfers(userId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(mockTransfers, response.getBody());
    }
}
