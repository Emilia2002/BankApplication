package com.bankapplication.management.service;

import com.bankapplication.management.dto.CreateCardRequest;
import com.bankapplication.management.entity.Accounts;
import com.bankapplication.management.entity.Cards;
import com.bankapplication.management.repository.CardsRepository;
import org.springframework.stereotype.Service;

import java.security.Provider;
import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

@Service
public class CardsService {

    private final CardsRepository cardsRepository;
    private final BankAccountService bankAccountService;

    public CardsService(CardsRepository cardsRepository, BankAccountService bankAccountService) {
        this.bankAccountService = bankAccountService;
        this.cardsRepository = cardsRepository;
    }

    // Generate a random 16-digit card number
    private String generateCardNumber() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 16; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    // Generate a random 3-digit CVV
    private String generateCVV() {
        SecureRandom random = new SecureRandom();
        int cvv = 100 + random.nextInt(900); // ensures 3 digits
        return String.valueOf(cvv);
    }

    // Generate expiry date in MM/YY format
    private String generateExpiryDate() {
        Random random = new Random();
        int month = 1 + random.nextInt(12);
        int year = 29 + random.nextInt(5); // 2029-2034
        return String.format("%02d/%02d", month, year);
    }

    public void generateCard(CreateCardRequest request) {
        Cards card = new Cards();

        // Fetch account from DB
        Accounts account = bankAccountService.getAccountById(request.getAccountId());

        if (account == null) { // check if the account exists
            throw new RuntimeException("Account not found");
        }

        card.setCardNumber(generateCardNumber());
        card.setCvv(generateCVV());
        card.setExpiryDate(generateExpiryDate());
        card.setCardType("DEBIT");
        card.setAccount(account);

        cardsRepository.save(card); // save the card details to the database
    }

    public List<Cards> getUserCreditCards(Long userId) {
        return cardsRepository.findByAccountOwnerId(userId);
    }
}
