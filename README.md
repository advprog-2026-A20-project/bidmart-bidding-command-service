# BidMart Bidding Command Service

`bidmart-bidding-command-service` adalah write-side/source of truth untuk command-side auction dan bid state.

## Tanggung Jawab

- create auction command
- activate auction command
- place bid command
- close auction command
- orchestration hold/release/capture ke `bidmart-wallet-service` melalui `WalletClient`

## Endpoint

- `POST /api/auctions`
- `POST /api/auctions/{auctionId}/activate`
- `POST /api/auctions/{auctionId}/bids`
- `POST /api/auctions/{auctionId}/close`
- `GET /actuator/health`

Endpoint query auction sengaja tidak disediakan di service ini. `GET /api/auctions`,
`GET /api/auctions/{auctionId}`, dan `GET /api/auctions/{auctionId}/bids`
dimiliki oleh `bidmart-auction-query-service`.

## Ownership Data

Service ini mengelola state command untuk tabel/aggregate:

- `auction`
- `bid`

Auth/user identity dan validasi listing harus masuk melalui boundary API, JWT, atau snapshot command-side.
Service ini tidak boleh melakukan direct repository/entity coupling ke bounded context auth, listing,
wallet, auction-query, atau listing-query.

## Dependency Direction

```text
Gateway -> Bidding Command Service -> Wallet Service
```

Frontend memanggil gateway, bukan service ini secara langsung.

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
