# Tests

Reference datasets and fixtures for acceptance verification (§16/§27):

- `reference/` — fixed datasets: unit conversions, sparse glucose, irregular
  BP timing, conflicting sources, timezone/DST changes, duplicate imports
- `prompts/` — prompt-injection samples for the insight pipeline (§27.9)
- `fixtures/` — recorded Health Connect / Samsung import samples (sanitized)

Analytics outputs must match deterministic computation from these datasets.
