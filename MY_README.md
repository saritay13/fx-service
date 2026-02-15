# FX Rate Service

This project is a **foreign exchange (FX) rate service** that exposes REST APIs to retrieve and compute EUR-based foreign exchange rates using data from the **Deutsche Bundesbank (ECB reference rates)**.

---

##  Supported Operations

### 1. Get all available currencies
Returns a list of all foreign currencies for which EUR exchange rates are available.

**Example**
```
GET /api/v1/currencies
```
**Current Implementation:**
For simplicity, this endpoint only retrieves and returns currency codes (e.g., `USD`, `GBP`, `JPY`).

**Recommendation:**
In a production environment, would consider returning enriched currency information:
```json
{
  "currencies": [
    {
      "code": "USD",
      "name": "United States Dollar",
      "symbol": "$"
    },
    {
      "code": "GBP",
      "name": "British Pound Sterling",
      "symbol": "£"
    }
  ]
}
```
---

### 2. Get all EUR-FX exchange rates for a currency (time series)
Fetches EUR-based exchange rates for a All currencies across multiple dates.

**Examples**
```
GET /api/v1/rates
GET /api/v1/rates?start=2024-01-01&end=2024-03-01
```

**Behavior**
- If `start` and `end` are not provided, the service defaults to the **last N available observations** (configurable)
- Date ranges are validated against a configurable maximum range to prevent excessive API calls

**Recommended Production Strategy:**
1. **Backfill historical data** into a database on initial setup
2. **Schedule periodic updates** to fetch and store new rates daily
3. **Serve requests from the database** with pagination support
4. **Benefits:**
  - Reduced external API dependency and latency
  - Better performance with indexed queries
  - Ability to handle large date ranges efficiently
  - Improved reliability and caching capabilities

The current implementation prioritizes simplicity and demonstrates API integration patterns, while the database-backed approach would be more suitable for production workloads with high traffic and complex querying requirements.

---

### 3. Get EUR-FX exchange rate for a specific day
Returns the list of exchange rate for all currencies on a specific date.

**Example**
```
GET /api/v1/rates/2025-03-02
```

Response meaning:
```
1 EUR = X USD
```

---

### 4. Convert a foreign currency amount to EUR
Converts a given foreign currency amount to EUR using the exchange rate on a specific day.

**Example**
```
GET /api/v1/convert?currency=USD&date=2025-02-03&amount=100
```

This means:
```
100 USD → EUR (based on rate from 2025-02-03)
```

---

##  Caching Strategy (In-Memory)

For simplicity, this implementation uses **in-memory caching**.

### How caching works

Two-level caching strategy is implemented for optimal performance:
1. **Individual Rate Cache**: `CurrencyDateKey(currency, date) → FxRate`
  - Used for currency conversion operations
  - Provides O(1) lookup for single currency-date pairs
  - Example: `(USD, 2024-01-15) → Rate 1.0945`

2. **Date-based Cache**: `LocalDate → Map<Currency, FxRate>`
  - Used when fetching all rates for a specific date
  - Provides O(1) lookup for all currencies on a given date
  - Reduces redundant API calls when requesting multiple currencies

 **Cache Flow:**
```
Request → Check Cache → Cache Hit? 
                         ├─ Yes → Return cached data (O(1))
                         └─ No  → Fetch from Bundesbank API
                                  → Store in cache
                                  → Return data
```

---

## Testing Strategy

### Current State
The controller tests are currently a mix of unit and integration tests. Ideally, these should be separated into:
- **Unit tests** using `@WebMvcTest` with mocked services (fast, isolated)
- **Integration tests** using `@SpringBootTest` with WireMock (slower, end-to-end)

Given time constraints, the current implementation demonstrates the basic flow and core functionality. The tests cover:
- Happy path scenarios
- Error handling (validation, upstream failures)
- Edge cases (invalid dates, amounts, ranges)

### Future Improvements
For a production-ready test suite, the tests should be properly separated:
- Controller unit tests → Validate request/response mapping and HTTP behavior
- Service integration tests → Test business logic with real external API interactions
- Clear separation of concerns for better maintainability and faster test execution

---

## Running the Application

### Clone the repository
```bash
git clone  -b master https://github.com/saritay13/fx-service.git
cd fx-service
```

### Run the application
```bash
./mvnw spring-boot:run
```

### Run tests
```bash
./mvnw test
```

---

## API Documentation (Swagger)

Once the service is running, access interactive API documentation at:

**Swagger UI**
```
http://localhost:8080/swagger-ui.html
```

---


