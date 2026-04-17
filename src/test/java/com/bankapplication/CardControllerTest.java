package com.bankapplication;

import com.bankapplication.management.controllers.CardController;
import com.bankapplication.management.dto.CreateCardRequest;
import com.bankapplication.management.service.CardsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;

class CardControllerTest {

    private CardsService cardsService;
    private CardController controller;

    @BeforeEach
    void setUp() {
        cardsService = Mockito.mock(CardsService.class);
        controller = new CardController(cardsService);
    }

    @Test
    void createCreditCard_returnsSuccessMessage() {
        CreateCardRequest request = new CreateCardRequest();
        request.setAccountId(1L);
        request.setCardType("DEBIT");
        request.setCardNumber("1234567890123456");

        Mockito.doNothing().when(cardsService).generateCard(any(CreateCardRequest.class));

        ResponseEntity<?> response = controller.createCreditCard(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(Map.of("message", "Credit Card created successfully"), response.getBody());
    }

    @Test
    void getUserCreditCards_returnsList() {
        Long userId = 1L;
        List<String> mockCards = List.of("Card1", "Card2");
        Mockito.when(cardsService.getUserCreditCards(userId)).thenReturn((List)mockCards);

        ResponseEntity<?> response = controller.getUserCreditCards(userId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(mockCards, response.getBody());
    }
}
