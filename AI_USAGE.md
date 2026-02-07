
# AI_USAGE.md

## AI Tools Used
- **ChatGPT (OpenAI)** – used as a conversational assistant for:
   - Understanding the Bundesbank FX API (SDMX)
   - Interpreting request/response structures
   - Designing API flows, caching strategies, and domain models

No code was blindly copy-pasted. All suggestions were reviewed, adapted, and implemented manually.

---

## 1. Research and Understanding of the Bundesbank FX API

### Prompt
How will I receive exchange rates from the Bundesbank Daily Exchange Rates public service?

### Relevant AI Response
The Deutsche Bundesbank provides an SDMX-based REST API:

```
https://api.statistiken.bundesbank.de/rest/data/{FLOW_REF}/{SERIES_KEY}
```

Examples:

Fetch all daily EUR-FX rates for USD:
```
curl -H "Accept: application/vnd.sdmx.data+json;version=1.0.0" \
"https://api.statistiken.bundesbank.de/rest/data/BBEX3/D.USD.EUR.BB.AC.000"
```

Fetch rates for a specific day:
```
curl -H "Accept: application/vnd.sdmx.data+json;version=1.0.0" \
"https://api.statistiken.bundesbank.de/rest/data/BBEX3/D.USD.EUR.BB.AC.000?startPeriod=2024-01-15&endPeriod=2024-01-15"
```

### Reasoning
The suggested endpoints matched the official Bundesbank documentation and were validated manually using curl before being used in code.

---

## 2. Interpreting the Request and Response

### Prompt
How do I interpret the request and response from the Bundesbank FX API?

### Key Interpretation

| Part    | Meaning                                   |
|---------|-------------------------------------------|
| BBEX3   | ECB euro foreign exchange reference rates |
| D       | Daily frequency                           |
| USD     | Foreign currency                          |
| EUR     | Base currency                             |
| BB      | Bundesbank / ECB series                  |
| AC      | Average / reference rate                 |
| 000     | Standard suffix                          |

Wildcard example for all currencies:
```
BBEX3/D..EUR.BB.AC.000
```

### Design Decision
The response indicates:
**1 EUR = X foreign currency**

I normalized the SDMX response into a clean internal model:
```json
{
  "date": "2024-01-15",
  "baseCurrency": "EUR",
  "foreignCurrency": "USD",
  "rate": 1.0945,
  "source": "ECB via Deutsche Bundesbank" 
}
```


---

## 3. Getting All Available Currencies

### Prompt
Assess my approach: cache → API on miss → extract currency codes → return result.

### AI Suggestions
- In-memory cache (simple)
- Database-backed cache (production-grade)

### Final Decision
I chose **in-memory caching** for simplicity:
- Currency lists change infrequently
- No persistence required for the assignment
- Keeps the solution minimal and readable

### Endpoint Used
```
curl -H "Accept: application/vnd.sdmx.data+json;version=1.0.0" \
"https://api.statistiken.bundesbank.de/rest/data/BBEX3/D..EUR.BB.AC.000?detail=serieskeyonly"
```

This returns only metadata, minimizing payload size. Below is sample response
![img_1.png](img_1.png)
---






