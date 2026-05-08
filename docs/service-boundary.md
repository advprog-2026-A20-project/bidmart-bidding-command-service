# Bidding Command Boundary

## In-Scope (Command/Write Side)

- Place bid command.
- Minimum increment validation.
- Auction lifecycle command (activate/close/cancel).
- Anti-sniping extension.
- Winner determination.
- Wallet orchestration: hold/release/capture.
- Event publish (`BidPlaced`, `AuctionExtended`, `AuctionClosed`, `WinnerDetermined`, `AuctionUnsold`).

## Out-of-Scope (Query/Read Side)

- `GET /api/auctions`
- `GET /api/auctions/{auctionId}`
- `GET /api/auctions/{auctionId}/bids`
- Analytics/statistik query

Endpoint query dimiliki `bidmart-auction-query-service`.

## Command API Minimal

- `POST /api/bids`
- `POST /api/auctions/{auctionId}/bids`
- `POST /api/auctions/{auctionId}/close`
- `POST /api/auctions/{auctionId}/cancel`

## Coupling yang Diperbolehkan Sementara

- Auth/User service untuk validasi identitas/permission.
- Listing service untuk validasi listing snapshot.
- Wallet service untuk transaksi hold/capture/release.

## Coupling yang Harus Dihilangkan

- Direct repository/entity access lintas bounded context (user/listing/wallet) dari monolith.
- Dependensi close auction pada endpoint query legacy.

## Asumsi Migrasi

- Karena source code monolith branch `feat/auction-query-rollout` belum diimport ke repo ini, boundary ditetapkan sebagai kontrak awal.
- Implementasi detail aggregate, repository, dan orchestration akan dipindahkan bertahap dari branch staging aman.
