package com.bankapplication.management.controllers;

import com.bankapplication.management.dto.CreateBankAccRequest;
import com.bankapplication.management.dto.TransferRequest;
import com.bankapplication.management.service.BankAccountService;
import com.bankapplication.management.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("bank")
@CrossOrigin(origins="*")
@Tag(name = "Bank Account Management", description = "Operations related to bank accounts and transfers")
public class BankAccountController {

    private final BankAccountService bankAccountService;
    private final TransferService transferService;

    public BankAccountController(BankAccountService bankAccountService, TransferService transferService) {
        this.bankAccountService = bankAccountService;
        this.transferService = transferService;
    }

    // Create a new user bank account
    @PostMapping("create-account")
    @Operation(summary = "Create a new account", description = "Generate a account for the current user")
    public ResponseEntity<?> createBankAccount(@RequestBody CreateBankAccRequest request) {
        bankAccountService.createBankAccount(request); // create new bank account based on the request
        return ResponseEntity.ok(
                Map.of("message", "Bank account created successfully")
        );
    }

    // Get all bank accounts for the current user
    @GetMapping("accounts/{userId}")
    @Operation(summary = "Fetch all accounts for the current user", description = "Display all bank accounts associated with the logged-in user")
    public ResponseEntity<?> getUserBankAccounts(@PathVariable Long userId) {
        return ResponseEntity.ok(bankAccountService.getUserBankAccounts(userId));
    }

    // Delete selected bank account
    @DeleteMapping("{accountId}/{userId}")
    @Operation(summary = "Delete a bank account", description = "Delete the specified bank account of the user")
    public ResponseEntity<?> deleteBankAccount(@PathVariable Long userId, @PathVariable Long accountId) {
        bankAccountService.deleteBankAccount(userId, accountId); // call delete method
        return ResponseEntity.noContent().build();
    }

    // Get the total amount from all bank accounts
    @GetMapping("total-amount/{userId}")
    @Operation(summary = "Get the total balance for the user", description = "Display the total balance, in euros, across all bank accounts of the user")
    public ResponseEntity<?> getTotalAmount(@PathVariable Long userId) {
        return ResponseEntity.ok(Map.of("totalAmount", bankAccountService.getTotalAmount(userId)));
    }

    // Generate a transfer between two bank accounts
    @PostMapping("transfer/{userId}")
    @Operation(summary = "Generate a transfer", description = "Generate a money transfer between two bank accounts")
    public ResponseEntity<?> generateTransfer(@RequestBody TransferRequest request) {
        transferService.transferFunds(request.getSenderAccountId(), request.getRecipientAccountNumber(), request.getAmount(), request.getDescription(), request.getDate());
        return ResponseEntity.ok(Map.of("message", "Transfer successful"));
    }

    // Get all transfers for the current user
    @GetMapping("transfers/{userId}")
    @Operation(summary = "Fetch all transfers for the current user", description = "Display all transfers associated with the logged-in user")
    public ResponseEntity<?> getUserTransfers(@PathVariable Long userId) {
        return ResponseEntity.ok(transferService.getUserTransfers(userId));
    }
}
