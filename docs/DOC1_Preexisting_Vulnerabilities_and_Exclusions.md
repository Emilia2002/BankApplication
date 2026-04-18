# DOCUMENT 1 - Pre-existing Vulnerabilities & Vulnerability Class Exclusions

**Course:** Cybersecurity
**Project:** FlexiBank (BankApplication backend + frontend-bank-app frontend)

---

## Part A - Vulnerability Classes That Could Not Be Implemented

All four mandatory vulnerability classes were successfully implemented:

1. **XSS** - Implemented (Stored XSS via transaction descriptions)
2. **SQL Injection** - Implemented (Native query concatenation in search endpoint)
3. **Auth** - Implemented (Two distinct auth vulnerabilities)
4. **Errors in app code** - Implemented (Two distinct logic errors)

No mandatory class had to be excluded. The application had sufficient surface area for all four categories.

**Note:** While the app's original architecture was shallow (no real auth layer, minimal business logic), each vulnerability was crafted to exploit a realistic attack vector that a production banking app could plausibly contain.

---

## Part B - Pre-existing Vulnerabilities Still Present in the Project

The following vulnerabilities were already present in the original codebase before any modifications. They remain unfixed.

---

### B.1 CORS: Unrestricted Origin (Backend)

**Location:** `AuthController.java`, `BankAccountController.java`, `CardController.java` - `@CrossOrigin(origins="*")` annotation on each controller class

**Description:**
Every REST controller is annotated with `@CrossOrigin(origins="*")`. Spring MVC resolves CORS configuration at two levels: a global `CorsConfigurationSource` bean (defined in `SecurityConfig.java`, restricting origins to `http://localhost:3000`) and per-controller/method `@CrossOrigin` annotations. When both are present, the annotation-level config takes precedence for that handler. Since the annotation specifies `"*"`, the global restriction is effectively overridden on all endpoints. The browser's Same-Origin Policy is the primary defense against cross-site request abuse, and this wildcard CORS header tells the browser to allow requests from any origin.

**Impact:**
An attacker can host a page on any domain (e.g., `evil.com`) containing JavaScript that issues `fetch()` or `XMLHttpRequest` calls to `http://localhost:8080/bank/accounts/1`. The browser will attach the `Origin` header, the server will respond with `Access-Control-Allow-Origin: *`, and the browser will allow the attacker's script to read the response body. This enables full cross-origin data exfiltration: account balances, card details, transaction history. Since the app has no token-based auth (see B.3), the attacker doesn't even need to steal a session cookie - they just need the victim's userId (an incrementing integer, trivially guessable). If the victim happens to be on the same network as the backend (e.g., `localhost` during development), the attacker's page can directly reach the API.

---

### B.2 Auth via localStorage (Frontend)

**Location:** `frontend-bank-app/components/auth-provider.tsx`

**Description:**
The login flow works as follows: the frontend `POST`s credentials to `/api/auth/login`, the backend verifies them against BCrypt hashes, and on success returns a plain JSON object `{id, email, fullName}`. The `AuthProvider` React context stores this object verbatim in `localStorage("user")`. On every page load, the provider reads `localStorage("user")` and parses it with `JSON.parse()` to reconstruct the user state. There is no server-issued token (JWT, opaque session token, etc.), no HTTP-only cookie, and no signature on the stored object. The backend has no concept of "sessions" - it never checks whether a request comes from someone who actually logged in. The userId in localStorage is simply passed as a URL path parameter in subsequent API calls.

**Impact:**
- **XSS -> full takeover:** Any XSS vulnerability (reflected or stored) gives the attacker access to `localStorage.getItem("user")`. Since the object contains the userId, the attacker can impersonate the victim by calling API endpoints with that ID. Unlike HTTP-only cookies, `localStorage` is fully accessible to JavaScript.
- **Client-side fabrication:** A user (or attacker with browser access) can open DevTools and run `localStorage.setItem("user", '{"id":1,"email":"a@b.com","name":"Admin"}')`, then refresh the page. The frontend will treat this as an authenticated session for user ID 1. The backend will serve all of user 1's data because it trusts the userId blindly (see B.3).
- **No logout invalidation:** The `logout()` function simply calls `localStorage.removeItem("user")`. There is no server-side session to revoke, so if an attacker has already copied the user object, the victim's "logout" does not invalidate the attacker's access.

---

### B.3 Open Endpoints - No Server-Side Authentication (Backend)

**Location:** `config/SecurityConfig.java` - `filterChain` method: `anyRequest().permitAll()`

**Description:**
The Spring Security `SecurityFilterChain` bean is configured with `auth -> auth.anyRequest().permitAll()`. This means Spring Security does not enforce any authentication or authorization check on any request. The security filter chain still runs (CORS headers are processed, etc.), but the authorization decision is always "permit." There is no JWT validation filter, no `UsernamePasswordAuthenticationFilter` configured for form login, no `SecurityContext` populated with a principal. Every controller method receives the userId as a `@PathVariable` or within the `@RequestBody` and passes it directly to the service layer, which uses it to query the database. The application implicitly trusts that the caller is the user identified by that ID.

This is an Insecure Direct Object Reference (IDOR) pattern at its most extreme: the entire API is an IDOR because there is literally no access control layer. The userId is the only "credential," and it's an auto-incrementing `BIGINT` primary key starting at 1.

**Impact:**
Any HTTP client (curl, Postman, a Python script, or the browser) can call any endpoint with any userId:

- `GET /bank/accounts/1` - returns all accounts belonging to user 1
- `GET /bank/accounts/2` - returns all accounts belonging to user 2
- `DELETE /bank/3/1` - deletes account 3 if it belongs to user 1
- `POST /bank/transfer/1` with `{"senderAccountId":5, ...}` - initiates a transfer from account 5 regardless of who owns it

An attacker can enumerate all users (IDs 1, 2, 3, ...), retrieve all their financial data, initiate transfers from their accounts, and delete their accounts. The attack requires zero authentication and can be fully automated.

---

### B.4 No Input Validation on DTOs (Backend)

**Location:** All controller methods accepting `@RequestBody` DTOs: `CreateBankAccRequest`, `CreateCardRequest`, `LoginRequest`, `RegisterRequest`, `TransferRequest`

**Description:**
The project includes `spring-boot-starter-validation` as a Maven dependency, and some JPA entity classes use `@NotBlank` on fields (e.g., `Accounts.accountName`). However, the validation framework only triggers when `@Valid` (or `@Validated`) is placed on the controller method parameter (e.g., `@RequestBody @Valid CreateBankAccRequest request`). None of the controllers use `@Valid`. Furthermore, the DTO classes themselves (which are what Jackson deserializes the JSON into) have zero validation annotations - no `@NotNull`, `@NotBlank`, `@Size`, `@Min`, `@Email`, or `@Pattern`. The entity-level annotations only trigger if Hibernate validation is enabled during `em.persist()` calls, but even that only covers the entity fields, not the DTO fields that are consumed before entity construction.

**Impact:**
- **Null fields accepted:** Sending `{"accountName": null, "currency": null, "balance": null, "userId": 1}` to `POST /bank/create-account` will either create an account with null fields (if the database columns are nullable) or throw an unhandled `DataIntegrityViolationException` that leaks internal stack traces to the client.
- **Arbitrarily long strings:** There are no `@Size` constraints, so a string field can be megabytes long, limited only by the HTTP request body size and the database column type (typically `VARCHAR(255)` which would cause a truncation or exception).
- **Negative initial balances:** `CreateBankAccRequest.balance` is a `Double` with no `@Min(0)` constraint. A user can create an account with balance -10000.
- **Malformed emails:** `RegisterRequest.email` has no `@Email` annotation; any string is accepted (e.g., `"not-an-email"`).
- **Attack amplifier:** The lack of input validation is what enables the inserted Stored XSS (Vuln 1) - the description field accepts `<script>` tags because nothing rejects or sanitizes them.

---

### B.5 Card Numbers and CVVs Stored/Displayed in Plaintext

**Location:**
- Backend: `management/entity/Cards.java`, `management/service/CardsService.java`
- Frontend: `app/dashboard/cards/page.tsx`

**Description:**
In `CardsService.generateCard()`, the 16-digit card number is generated via `SecureRandom` and stored as a plain `String` in the `Cards` entity's `cardNumber` field. The CVV is similarly generated (via insecure `java.util.Random`, see B.9) and stored as a plain `String` in the `cvv` field. The JPA entity maps these to regular `VARCHAR` database columns with no encryption, no hashing, and no column-level database encryption. The `GET /card/total-cards/{userId}` endpoint returns the full `Cards` entity via Jackson serialization, exposing all fields: `cardNumber`, `cvv`, `expiryDate`, `cardType`. The frontend renders these values in full on the cards page - the card number is displayed as all 16 digits, and the CVV is shown as-is, with no masking (e.g., no `**** **** **** 1234` pattern).

**Impact:**
- **Database breach = full card compromise:** If the database is dumped (via SQL injection, backup theft, or misconfigured access), every card number and CVV is immediately available in cleartext. No decryption key is needed.
- **API-level exposure:** Any vulnerability that allows reading API responses (XSS, IDOR, CORS abuse) also exposes complete card data. The card number + CVV + expiry date is sufficient for Card-Not-Present (CNP) fraud.
- **PCI-DSS violation:** PCI-DSS Requirement 3.4 mandates that the Primary Account Number (PAN) must be rendered unreadable anywhere it is stored (encryption, truncation, hashing, or tokenization). Requirement 3.2 prohibits storing the CVV/CVC2 after authorization under any circumstances, even encrypted. This application violates both requirements.

---

### B.6 Database Credentials Hardcoded in application.properties

**Location:** (When `application.properties` exists - currently missing from repo, which means the app cannot start without external configuration. The vulnerability applies once the file is added for deployment.)

**Description:**
The Spring Boot application requires `spring.datasource.url`, `spring.datasource.username`, and `spring.datasource.password` to connect to PostgreSQL. The project has no `application.properties` or `application.yml` in the repository (the `src/main/resources` directory is absent), meaning the developer must add one before the app can start. The standard pattern for projects of this level is to write the credentials directly into `application.properties` and commit the file to Git. There is no evidence of environment variable substitution (`${DB_PASSWORD}`), Spring Cloud Config, or any secrets management integration in the codebase.

**Impact:**
- **Credential exposure:** Once `application.properties` is committed with database credentials, anyone with read access to the repository (team members, CI/CD systems, forks, and - if the repo is accidentally made public - the entire internet) obtains the production database username and password.
- **Lateral movement:** Database credentials often have broad permissions (read/write all tables, potentially `CREATE`/`DROP`). An attacker with these credentials can bypass the application entirely and connect to the database directly, exfiltrating or modifying all data.
- **Git history persistence:** Even if the credentials are later removed from the file, they remain in the Git history forever unless the history is rewritten (e.g., `git filter-branch` or BFG Repo-Cleaner).

---

### B.7 CSRF Protection Disabled (Backend)

**Location:** `config/SecurityConfig.java` - `csrf.disable()`

**Description:**
The `SecurityFilterChain` explicitly disables CSRF protection via `csrf(csrf -> csrf.disable())`. In Spring Security, CSRF protection works by requiring a server-generated token on state-changing requests (`POST`, `PUT`, `DELETE`). When disabled, the server accepts state-changing requests from any origin without a CSRF token. This is a standard and correct configuration for stateless REST APIs that authenticate via `Authorization: Bearer <token>` headers, because CSRF attacks rely on the browser automatically attaching cookies, and Bearer tokens are not automatically attached. However, this application has no authentication mechanism at all (see B.3), so the "stateless API" justification does not apply.

**Impact:**
Currently, the practical impact is subsumed by B.3 (open endpoints) - since there's no auth to protect, CSRF is moot. However, if session-based authentication (e.g., Spring Security's `httpBasic()` or `formLogin()` with `JSESSIONID` cookies) were added in the future without re-enabling CSRF, the application would immediately be vulnerable to CSRF attacks. A malicious website could include a hidden form that `POST`s to `/bank/transfer/{userId}` with crafted parameters, and the browser would automatically attach the session cookie, executing the transfer on behalf of the logged-in user without their knowledge.

---

### B.8 getTotalAmount() Mutates JPA-Managed Entity Balances (Backend)

**Location:** `management/service/BankAccountService.java` - `getTotalAmount()`

**Description:**
The `getTotalAmount()` method fetches all accounts for a user via `getUserBankAccounts(userId)`, which returns a `List<Accounts>` from `AccountsRepository.findByOwner_Id(userId)`. These are JPA-managed (attached/persistent) entity instances - they exist in Hibernate's first-level cache (the persistence context). The method then iterates over them and, for any RON-denominated account, calls `accounts.get(i).setBalance(accounts.get(i).getBalance() / 5.09)` to convert the balance to EUR for aggregation. This mutation happens on the managed entity instance itself.

Hibernate's dirty-checking mechanism works as follows: at the end of a transaction (or when `EntityManager.flush()` is called), Hibernate compares the current state of all managed entities against their state when they were loaded. If any field has changed, Hibernate generates an `UPDATE` SQL statement and persists the change. Since `setBalance()` modifies the entity's `balance` field, Hibernate will detect this as a "dirty" change and flush it to the database if the method executes within a transactional context (e.g., if called from another `@Transactional` method, or if Spring's `OpenEntityManagerInView` interceptor keeps the persistence context open through the HTTP request).

**Impact:**
Every call to `GET /bank/total-amount/{userId}` can permanently divide every RON account's balance by 5.09 and write the corrupted value to the database. If the endpoint is called `n` times, each RON balance is divided by `5.09^n`. After just 5 calls, a 1000 RON balance becomes `1000 / 5.09^5 ≈ 2.88 RON`. The data corruption is silent - no error is thrown, no log is written, and the API returns the (first-time correct) total. The fix would be to either use a DTO/projection for the calculation (not mutating the entity), or to use `@Transactional(readOnly = true)` which would cause Hibernate to skip dirty-checking, or to detach the entities before modifying them.

---

### B.9 CVV Generated with Insecure Random (Backend)

**Location:** `management/service/CardsService.java` - `generateCVV()`

**Description:**
The `generateCardNumber()` method uses `java.security.SecureRandom`, which is a cryptographically secure pseudo-random number generator (CSPRNG) backed by the OS entropy pool (`/dev/urandom` on Linux, `CryptGenRandom` on Windows). However, `generateCVV()` and `generateExpiryDate()` both use `java.util.Random`, which is a linear congruential generator (LCG) with a 48-bit seed. The `new Random()` constructor seeds itself from `System.nanoTime()`, which is not cryptographically random. Given the LCG's small state space and deterministic nature, if an attacker can observe or estimate the seed (e.g., by knowing the approximate time a card was generated and observing one generated value), they can reconstruct the `Random` instance's state and predict all subsequent outputs, including CVVs and expiry dates.

**Impact:**
In isolation, this is medium severity - predicting CVVs requires effort and partial knowledge. But combined with B.5 (CVVs are stored and returned in plaintext anyway), the insecure PRNG is somewhat academic. The real concern is the pattern it establishes: the codebase mixes secure and insecure randomness inconsistently, which suggests the developer may not understand the distinction. In a production system where CVVs were properly encrypted at rest, this insecure generation would be the remaining weak link.

---

### B.10 Double Used for Monetary Values (Backend)

**Location:** All entity balance/amount fields (`Accounts.balance`, `Transfers.amount`), all service calculations (`TransferService.transferFunds()`, `BankAccountService.getTotalAmount()`)

**Description:**
All monetary values in the application use Java's `Double` (boxed) or `double` (primitive) type. `Double` is an IEEE 754 64-bit floating-point representation, which stores numbers in binary scientific notation (sign + 11-bit exponent + 52-bit mantissa). This format cannot exactly represent most decimal fractions. For example, `0.1` in binary is a repeating fraction (`0.0001100110011...`), so `0.1 + 0.2` evaluates to `0.30000000000000004`, not `0.3`. The correct type for monetary values in Java is `BigDecimal`, which stores numbers as arbitrary-precision decimal (base-10) values and provides explicit rounding modes (`HALF_UP`, `HALF_EVEN`/banker's rounding, etc.).

The transfer logic compounds this: `senderAccount.setBalance(senderAccount.getBalance() - amount)` performs floating-point subtraction. Over many transactions, rounding errors accumulate. The currency conversion (`amount / 5.09` or `amount * 5.09`) introduces additional floating-point imprecision. The `getTotalAmount()` method uses `stream().mapToDouble().sum()`, which aggregates floating-point errors across all accounts.

**Impact:**
- **Balance check bypass:** The `if (senderAccount.getBalance() < amount)` check uses floating-point comparison. Due to rounding, a balance that should be exactly equal to the transfer amount might be represented as slightly less or slightly more, causing incorrect acceptance or rejection of transfers.
- **Ledger discrepancies:** Over thousands of transactions, the accumulated rounding error means the sum of all account balances will not match the expected total. In a real banking system, this causes reconciliation failures and audit flags.
- **Non-deterministic behavior:** The same sequence of operations may produce different results on different JVM implementations or hardware architectures (though in practice, IEEE 754 compliance makes this rare on modern x86/ARM CPUs).
