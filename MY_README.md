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

---

### 2. Get all EUR-FX exchange rates for a currency (time series)
Fetches EUR-based exchange rates for a given currency across multiple dates.

**Examples**
```
GET /api/v1/rates?currency=USD
GET /api/v1/rates?currency=USD&start=2024-01-01&end=2024-03-01
```

**Behavior**
- If `start` and `end` are not provided, the service defaults to the **last 365 available observations**
- Date ranges are validated (max 5 years)

---

### 3. Get EUR-FX exchange rate for a specific day
Returns the exchange rate for a given currency on a specific date.

**Example**
```
GET /api/v1/rates/USD/2025-03-02
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
- Exchange rates are cached using a `(currency, date)` key
- Before calling the Bundesbank API, the cache is checked
- If a cache hit occurs → response is returned immediately
- If a cache miss occurs → data is fetched from the upstream API, cached, and returned

### Why in-memory?
- Keeps the solution simple and fast
- Avoids persistence complexity for this assignment
- Cache is cleared between tests to ensure isolation

---

## Testing Strategy

- **Integration tests** cover:
    - All happy paths 
    - Validation failures (400)
    - Rate not found scenarios (404)
    - Upstream failures (503)
- External Bundesbank API calls are mocked using **WireMock**
- Cache behavior is verified within integration tests

> Unit tests for individual services can be added later; integration tests were prioritized to validate full request/response flows.

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


