package com.bankapplication.management.service;

import com.bankapplication.management.entity.Transfers;
import com.bankapplication.management.exceptions.AccountNotFoundException;
import com.bankapplication.management.exceptions.InsufficientFundsException;
import com.bankapplication.management.exceptions.InvalidTransferException;
import com.bankapplication.management.repository.AccountsRepository;
import com.bankapplication.management.entity.Accounts;
import com.bankapplication.management.repository.JDBCBankRepository;
import com.bankapplication.management.repository.TransfersRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.util.regex.Pattern;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TransferService {

    private final JDBCBankRepository BankRepository;
    private final AccountsRepository AccountsRepository;
    private final TransfersRepository TransfersRepository;
    private final BankAccountService bankAccountService;
    private final UserService userService;

    public TransferService(JDBCBankRepository BankRepository, AccountsRepository AccountsRepository, UserService UserService, BankAccountService BankAccountService, TransfersRepository TransfersRepository) {
        this.BankRepository = BankRepository;
        this.AccountsRepository = AccountsRepository;
        this.TransfersRepository = TransfersRepository;
        this.userService = UserService;
        this.bankAccountService = BankAccountService;
    }

    public boolean isValidatedSender(Long senderId) {
        var account = AccountsRepository.findByOwner_Id(senderId);
        return account != null && !account.isEmpty();
    }

    public boolean isValidatedRecipient(String recipientAccountNumber) {
        var account = BankRepository.findByAccountNumber(recipientAccountNumber);
        return account != null;
    }

    public boolean hasSufficientFunds(Long senderId, Double amount) {
        return bankAccountService.getTotalAmount(senderId) >= amount;
    }

    // logic for transferring the funds between user's accounts
    @Transactional
    public void transferFunds(Long senderAccountId, String recipientAccountNumber, Double amount, String description, LocalDateTime date) {
        if (description != null && !description.isEmpty()) {
            Pattern pattern = Pattern.compile("^([a-zA-Z0-9_<>=/\"'.-]+\\s?)+$");
            if (!pattern.matcher(description).matches()) {
                throw new IllegalArgumentException("Invalid description format. Only letters, numbers, underscores and spaces are allowed.");
            }
        }
        Accounts senderAccount = AccountsRepository.findById(senderAccountId).orElseThrow(() -> new IllegalArgumentException("Sender account not found"));
        Accounts recipientAccount = BankRepository.findByAccountNumber(recipientAccountNumber);

        if (recipientAccount == null) // check if recipient account exists
            throw new AccountNotFoundException("Recipient account not found");

        if (senderAccount.getBalance() < amount) // check if the sender has sufficient funds
            throw new InsufficientFundsException("Insufficient funds");

        // check if sender and recipient have the same bank acccount
        if (senderAccount.getAccountNumber().equals(recipientAccount.getAccountNumber()))
            throw new InvalidTransferException("Sender and recipient accounts cannot be the same");

        date = LocalDateTime.now();
        // check currency and transform if needed from RON to EUR or EUR to RON
        double convertedAmount = amount;

        if (!senderAccount.getCurrency().equals(recipientAccount.getCurrency())) {
            if (senderAccount.getCurrency().equals("RON") && recipientAccount.getCurrency().equals("EUR")) {
                convertedAmount = amount / 5.09;
            } else if (senderAccount.getCurrency().equals("EUR") && recipientAccount.getCurrency().equals("RON")) {
                convertedAmount = amount * 5.09;
            } else {
                throw new IllegalArgumentException("Currency conversion failed");
            }
        }

        senderAccount.setBalance(senderAccount.getBalance() - amount); // substract and set new balance
        recipientAccount.setBalance(recipientAccount.getBalance() + convertedAmount);
        AccountsRepository.save(senderAccount); // save changes for sender account
        BankRepository.save(recipientAccount); // save changes for recipient account

        Transfers transfer = new Transfers();
        transfer.setOwner(senderAccount.getOwner());
        transfer.setSenderAccount(senderAccount);
        transfer.setRecipientAccountNumber(recipientAccount.getAccountNumber());
        transfer.setAmount(amount);
        transfer.setDescription(description);
        transfer.setDate(date);

        TransfersRepository.save(transfer);

    }

    public List<Transfers> getUserTransfers(Long userId) {
        return TransfersRepository.findByOwner_Id(userId);
    }


}
