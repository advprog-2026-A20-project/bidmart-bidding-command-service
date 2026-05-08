# BidMart Bidding Command Service

`bidmart-bidding-command-service` adalah service command/write-side untuk domain bidding dan auction lifecycle pada arsitektur microservice BidMart.

## Fungsi Service

Service ini bertanggung jawab untuk:

- Place bid command.
- Validasi bid (minimum increment, status auction, bidder eligibility).
- Anti-sniping extension.
- Auction lifecycle command (close/cancel/activate jika diperlukan).
- Winner determination.
- Publish event untuk sinkronisasi read model dan service downstream.

## Batasan dengan Auction Query Service

- **Bidding Command Service**: mutasi data/command (`POST`, state transition, orchestration wallet).
- **Auction Query Service**: endpoint read-heavy (`GET auction list`, `GET auction detail`, `GET bid history`, leaderboard/projection).

Service ini **tidak** memiliki endpoint query berat agar CQRS boundary tetap jelas.

## Endpoint Command (Scaffold)

- `POST /api/bids`
- `POST /api/auctions/{auctionId}/bids`
- `POST /api/auctions/{auctionId}/close`
- `POST /api/auctions/{auctionId}/cancel`
- `GET /actuator/health`

> Catatan: saat ini endpoint masih scaffold/inisialisasi dan belum terhubung ke persistence + integration client.

## Event Contract Minimal

Lihat detail payload di `docs/event-contracts.md`.

- `BidPlaced`
- `AuctionExtended`
- `AuctionClosed`
- `WinnerDetermined`
- `AuctionUnsold`

## Dependency ke Service Lain

- **Auth/User service**: validasi token, profil user, role/permission.
- **Listing service**: validasi listing snapshot dan status listing.
- **Wallet service**: hold fund, release hold, capture hold.
- **Auction query service**: consumer event untuk membentuk read model auction.

## Data Ownership

Service ini akan menjadi owner untuk aggregate command:

- `Auction` (state command-side: created, active, closing, closed, cancelled).
- `Bid` (urutan bid, leader saat ini, validasi increment).

Read model tidak di-host di service ini.

## Run Lokal

```bash
./gradlew bootRun
```

Default port: `8085`

## Test

```bash
./gradlew test
```

## Risiko Teknis

- Concurrent bid dapat menyebabkan race condition jika locking/versioning belum ketat.
- Risiko double hold wallet pada retry tanpa idempotency key.
- Stale read model antara command vs query projection.
- Event publish gagal setelah commit command bila outbox belum diterapkan.

## Coupling yang Masih Harus Diputus

- Coupling ke model user/listing/wallet dari monolith lama (jika masih direct repository call) harus diganti jadi API client/async contract.
- Penutupan auction yang masih bergantung query legacy harus dipindah penuh ke command-side scheduler/orchestrator.

## Status Migrasi Saat Ini

- Repo ini baru tahap inisialisasi service + API contract command.
- Pemindahan class produksi dari repo monolith source branch `feat/auction-query-rollout` **belum dilakukan penuh** di repository ini.
- Detail boundary ada di `docs/service-boundary.md`.
