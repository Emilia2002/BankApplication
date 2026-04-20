package com.bankapplication.management.service;

import com.bankapplication.management.dto.CreateBankAccRequest;
import com.bankapplication.management.entity.Accounts;
import com.bankapplication.management.entity.Users;
import com.bankapplication.management.repository.AccountsRepository;
import com.bankapplication.management.repository.JDBCBankRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class BankAccountService {

    private final JDBCBankRepository BankRepository;
    private final AccountsRepository AccountsRepository;
    private final UserService userService;
    @PersistenceContext
    private EntityManager entityManager;

    public BankAccountService(JDBCBankRepository BankRepository, AccountsRepository AccountsRepository, UserService UserService) {
        this.BankRepository = BankRepository;
        this.AccountsRepository = AccountsRepository;
        this.userService = UserService;
    }

    private String generateAccountNumber() {
        return "RO" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
    }

    public void createBankAccount(CreateBankAccRequest request) {
        Accounts account = new Accounts();
        Users user = userService.findById(request.getUserId());

        account.setAccountName(request.getAccountName());
        account.setCurrency(request.getCurrency());
        account.setBalance(request.getBalance());
        account.setAccountNumber(generateAccountNumber());
        account.setOwner(user);

        BankRepository.save(account);
    }

    public void deleteBankAccount(Long userId, Long accountId) {
        Accounts account = AccountsRepository.findById(accountId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        if (!account.getOwner().getId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Unauthorized to delete this account");
        }
        AccountsRepository.delete(account); // delete the account
    }

    public List<Accounts> getUserBankAccounts(Long userId) {
        return AccountsRepository.findByOwner_Id(userId);
    }

    public Accounts getAccountById(Long accountId) {
        return AccountsRepository.findById(accountId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    @Transactional(readOnly = true)
    public Double getTotalAmount(Long userId) {
        List<Accounts> accounts = getUserBankAccounts(userId);
        for (int i = 0; i < accounts.size(); i++) {
            if (Objects.equals(accounts.get(i).getCurrency(), "RON")) {
                accounts.get(i).setBalance(accounts.get(i).getBalance() / 5.09);
            }
        }
        return accounts.stream()
                .mapToDouble(Accounts::getBalance)
                .sum();
    }

    @SuppressWarnings("unchecked")
    public List<Accounts> searchAccountsByName(String accountName) {
        String query = "SELECT * FROM bank.accounts WHERE account_name LIKE '%"
                + accountName + "%'";
        return entityManager.createNativeQuery(query, Accounts.class)
                .getResultList();
    }
}
