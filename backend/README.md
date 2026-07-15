# Coefficient Radar Backend (Ktor)

Secure gateway for **legal** demand-coefficient providers.

## Principles

- Provider secrets (`OFFICIAL_DEMAND_URL`, `OFFICIAL_DEMAND_TOKEN`) live **only** on the server.
- Android app never receives third-party provider keys.
- Responses always include source metadata and `is_real_data` / `is_demo`.
- No scraping of Yandex Pro, no traffic interception, no closed APIs.
- If official provider is not configured, `/v1/cities/{id}/zones` returns **empty** real data (honest).

## Run locally

```bash
# from backend/
# Windows: use Gradle from Android Studio or install Gradle 8.12+
gradle run
```

Health check:

```bash
curl http://localhost:8080/health
```

## Docker

```bash
docker build -t coefficient-radar-backend .
docker run --rm -p 8080:8080 --env-file .env coefficient-radar-backend
```

## Android connection

In `android/local.properties`:

```properties
DEMAND_API_BASE_URL=http://10.0.2.2:8080
DEMAND_API_TOKEN=
MAPS_API_KEY=your_google_maps_key
```

## Connecting a real provider

1. Sign a legal agreement with a data provider.
2. Set `OFFICIAL_DEMAND_URL` and `OFFICIAL_DEMAND_TOKEN` in the environment.
3. Implement the HTTP client branch in `DemandService.getZones()` to normalize their payload into `DemandZoneDto`.
4. Set `is_real_data=true` only for values that truly come from that provider.

## Privacy

- `DELETE /v1/users/{userId}` — placeholder for GDPR-style deletion (this demo stores no PII).
- Do not log coordinates with user identifiers longer than needed for debugging.
