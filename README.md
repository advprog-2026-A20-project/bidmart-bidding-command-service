# BidMart Bidding Command Service

`bidmart-bidding-command-service` adalah write-side/source of truth untuk command auction dan bidding.

## Tanggung Jawab

- create auction command
- activate auction command
- place bid command
- close auction command
- orchestration hold/release/capture ke `bidmart-wallet-service`

## Endpoint

- `POST /api/auctions`
- `POST /api/auctions/{auctionId}/activate`
- `POST /api/auctions/{auctionId}/bids`
- `POST /api/auctions/{auctionId}/close`
- `GET /actuator/health`

## Ownership Data

Service ini mengelola state command untuk tabel/aggregate:

- `auction`
- `bid`
- listing snapshot command-side yang dibutuhkan untuk validasi bid

Read endpoint auction/listing tetap dilayani query services.

## Environment

Lihat `.env.example`. Variabel utama:

- `PORT` (default `8084`)
- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`
- `JWT_SECRET`, `JWT_EXP_SECONDS`
- `CORS_ALLOWED_ORIGINS`
- `WALLET_SERVICE_BASE_URL`

## Local Run

```bash
cp .env.example .env
./gradlew bootRun
```

## Test

```bash
./gradlew test
```

## Docker

```bash
docker build -t bidmart-bidding-command-service .
docker run --env-file .env -p 8084:8084 bidmart-bidding-command-service
```

## Catatan Migrasi

Gateway tidak lagi menjalankan business logic auction command; endpoint command auction di gateway sekarang diproxy ke service ini.
