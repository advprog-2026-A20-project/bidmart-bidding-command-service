# BidMart Bidding Command Service

Repository ini adalah bounded context command/write-side untuk domain bidding dan auction lifecycle pada BidMart.

Service ini diposisikan sebagai bagian dari migrasi strangler pattern dari monolith (`bidmart-gateway` legacy) ke arsitektur microservice multi-repo.

## Fungsi Service

Service ini bertanggung jawab untuk:

- Place bid.
- Validasi aturan bid (minimum increment, auction aktif, bidder valid).
- Orkestrasi anti-sniping extension saat bid masuk mendekati waktu selesai.
- Auction lifecycle command-side (close/cancel).
- Winner determination saat penutupan auction.
- Publish event domain untuk sinkronisasi service lain.

## Bukan Tanggung Jawab Service Ini

Service ini **tidak** menangani endpoint read-heavy/query, termasuk:

- daftar auction,
- detail auction read model,
- histori bid untuk konsumsi UI.

Semua query tersebut adalah tanggung jawab `bidmart-auction-query-service`.

## Endpoint Command (Minimum Contract)

```txt
POST /bids
POST /auctions/{auctionId}/bids
POST /auctions/{auctionId}/close
POST /auctions/{auctionId}/cancel
GET  /actuator/health
```

> Catatan: endpoint masih tahap bootstrap/scaffold. Implementasi domain logic penuh akan dipindahkan bertahap dari monolith setelah inventaris final source branch tersedia.

## Event yang Dipublish (Minimum Contract)

- `BidPlaced`
- `AuctionExtended`
- `AuctionClosed`
- `WinnerDetermined`
- `AuctionUnsold`

Detail payload awal ada di `docs/event-contracts.md`.

## Dependency ke Service Lain

- **Auth/User service**: validasi token, profile user, permission role buyer/seller/admin.
- **Listing service**: validasi listing snapshot/status dan ownership.
- **Wallet service**: hold fund, release hold, capture hold.
- **Auction query service**: menerima event/projection dari command side (bukan dependency sinkron utama).

## Data Ownership

Target data ownership pada repo ini:

- Aggregate `Auction` (write-side status transition).
- Aggregate `Bid`.
- Invariant bidding dan lifecycle rules.
- Outbox/event publishing records.

## Run Lokal

```bash
./gradlew bootRun
```

Default port: `8085`.

## Test

```bash
./gradlew test
```

## Risiko Migrasi Utama

- Concurrent bid race condition.
- Double hold wallet ketika retry/idempotency belum stabil.
- Stale read model pada query service saat event delivery terlambat.
- Coupling lama ke repository/entity monolith (listing/user/wallet) yang perlu diputus via adapter client + contract.

## Coupling yang Masih Harus Diputus

Karena source branch monolith belum bisa di-clone dari environment ini (akses jaringan GitHub diblokir), coupling detail per class belum tervalidasi otomatis. Asumsi coupling yang harus diputus:

- direct repository access ke data user/listing/wallet,
- sinkronisasi close auction yang masih bergantung query endpoint lama,
- publish event tanpa outbox transactional guarantee.

Lihat rencana detail di `docs/service-boundary.md` dan `docs/migration-inventory.md`.
