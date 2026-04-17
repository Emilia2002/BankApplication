package com.bankapplication.management.controllers;

import com.bankapplication.management.dto.CreateCardRequest;
import com.bankapplication.management.service.CardsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("card")
@CrossOrigin(origins="*")
@Tag(name = "Card Management", description = "Operations related to debit/credit cards")
public class CardController {

    private final CardsService cardsService;

    public CardController(CardsService cardsService) {
        this.cardsService = cardsService;
    }

    // Create a new user bank account
    @PostMapping("create")
    @Operation(summary = "Create a new card", description = "Generate a new debit or credit card for a given account")
    public ResponseEntity<?> createCreditCard(@RequestBody CreateCardRequest request) {
        cardsService.generateCard(request); // create new credit card based on the request
        return ResponseEntity.ok(Map.of("message", "Credit Card created successfully"));
    }

    // Get all card for the current user
    @GetMapping("total-cards/{userId}")
    @Operation(summary = "Get user cards", description = "Get all cards for a specific user by userId")
    public ResponseEntity<?> getUserCreditCards(@PathVariable Long userId) {
        return ResponseEntity.ok(cardsService.getUserCreditCards(userId));
    }
}
