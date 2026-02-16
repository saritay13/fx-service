# FX Rate Service

This project is a **foreign exchange (FX) rate service** that exposes REST APIs to retrieve and compute EUR-based foreign exchange rates using data from the **Deutsche Bundesbank (ECB reference rates)**.

---

##  Supported Operations

### 1. Get all available currencies
Returns all available currencies as structured metadata (`code`, `name`) plus a `count`.

**Example**
```
GET /api/v1/currencies
```
Notes:
- Currency names are resolved via Java `Currency` metadata.
- Unknown upstream codes are safely mapped to `"Unknown currency"`.

---
---

### 2. Get EUR-FX exchange rates as a time-series collection
Fetches EUR-based exchange-rate series for all available currencies over a date range.

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
  - Improved reliability and caching capabilities~~

The current implementation prioritizes simplicity and demonstrates API integration patterns, while the database-backed approach would be more suitable for production workloads with high traffic and complex querying requirements.

---

### 3. Get EUR-FX exchange rate for a specific day
Returns all EUR-based rates available on a specific date.
#### If no observations exist, response includes a `failures.NO_DATA` message.
**Example**
```
GET /api/v1/rates/2025-03-02
```

---

### 4. Convert foreign currency amount to EUR
Converts a given foreign-currency amount to EUR using that day’s rate.
#### If a single rate is missing for the requested currency/date, API returns `404` via `RateNotFoundException` mapping.
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

### Implemented cache layers
1. **Individual rate cache**: `CurrencyDateKey(currency, date) -> FxRate`
2. **Date cache**: `LocalDate -> List<FxRate>`
3. **Series range cache**: `(start, end) -> List<FxRateSeriesResponse>` with TTL


 **Cache Flow:**
```
Request → Check Cache → Cache Hit? 
                         ├─ Yes → Return cached data (O(1))
                         └─ No  → Fetch from Bundesbank API
                                  → Store in cache
                                  → Return data
```
### Notes
- Series cache is centralized in `FxRateCache` and used by `/api/v1/fx/rates` paths.
- Bundesbank `404` responses are treated as **no-data** (empty optional/map), not hard upstream failures.
---

## Testing Strategy

### Current State
The controller tests are currently a mix of unit and integration tests. Ideally, these should be separated into:
- **Unit tests**  → Validate request/response mapping and HTTP behavior
- **Integration tests** → Validate end to end flow

Given time constraints, the current implementation demonstrates the basic flow and core functionality. The tests cover:
- Happy path scenarios
- Error handling (validation, upstream failures)
- Edge cases (invalid dates, amounts, ranges)
---

## Running the Application

### Clone the repository
```bash
git clone -b master https://github.com/saritay13/fx-service.git
cd fx-service
```

### Run the application
```bash
./mvnw spring-boot:run
```

### Run test
```bash
./mvnw test
```

---

## API Documentation (Swagger)

Once the service is running:

**Swagger UI**
```
http://localhost:8080/swagger-ui.html
```

---

### Future Enhancements
While the current implementation meets the functional and non-functional requirements of this challenge,
there is **significant scope for refactoring and hardening**, including but not limited to:

- Improved cache eviction strategies
- Better abstraction and separation of concerns
- More comprehensive error classification
- Externalize error messages into shared constants
- Add unit tests for individual components (cache, client, services)
- Persist FX rates in a database
- Add pagination for large time-series responses
- Introduce retry & timeout logic for Bundesbank API calls

---
