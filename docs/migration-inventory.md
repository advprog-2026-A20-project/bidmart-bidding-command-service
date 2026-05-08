# Inventaris Migrasi Bidding Command

Dokumen ini menjadi checklist pemindahan dari branch sumber monolith `feat/auction-query-rollout` ke `bidmart-bidding-command-service`.

## Status Verifikasi Source

- **Status saat ini**: belum terverifikasi langsung dari source repository karena clone GitHub gagal dari environment eksekusi (`CONNECT tunnel failed, response 403`).
- **Dampak**: daftar class di bawah bersifat **asumsi terstruktur** berdasarkan boundary domain dan naming pattern umum di monolith Java Spring.

## Kandidat Komponen yang Harus Dipindahkan

### Controller (Command)

- Bid Controller (mis. `BidController`) untuk:
  - `POST /bids`
  - `POST /auctions/{auctionId}/bids`
- Auction Command Controller (mis. `AuctionCommandController`) untuk:
  - `POST /auctions/{auctionId}/close`
  - `POST /auctions/{auctionId}/cancel`

### Service (Write-side)

- Bid Service (mis. `BidService`):
  - validasi amount,
  - validasi eligibility bidder,
  - place bid,
  - trigger anti-sniping.
- Auction Command Service (mis. `AuctionService` write-side):
  - lifecycle transition,
  - close/cancel auction,
  - winner determination,
  - unsold decision.

### Repository

- Bid Repository (mis. `BidRepository`).
- Auction Repository (mis. `AuctionRepository`).

### Entity / Model

- Auction entity/model write-side.
- Bid entity/model write-side.

### Validation & Domain Rule

- minimum increment,
- auction status transition guard,
- anti-sniping extension window,
- idempotency key untuk command retried,
- winner determination dan reserve handling.

### Event Publishing

- Publisher/adaptor event bus (Kafka/Rabbit/HTTP/event relay).
- Outbox record jika pattern outbox sudah ada di monolith.

## Yang Tidak Dipindahkan ke Repo Ini

- endpoint query auction (list/detail/history),
- listing query,
- wallet domain logic,
- auth domain logic,
- notification projection/read endpoint.

## Kontrak Endpoint Target

- `POST /bids`
- `POST /auctions/{auctionId}/bids`
- `POST /auctions/{auctionId}/close`
- `POST /auctions/{auctionId}/cancel`

## Kontrak Event Target

- `BidPlaced`
- `AuctionExtended`
- `AuctionClosed`
- `WinnerDetermined`
- `AuctionUnsold`

## Risiko Berikutnya

1. Gap perilaku jika implementasi anti-sniping di monolith tersebar di scheduler/query layer.
2. Potensi race condition saat parallel bid jika locking strategy belum disepakati.
3. Potensi mismatch wallet hold/capture ketika auction close event terlambat.
4. Risiko data divergence bila outbox belum dipakai.

## Next Step Owner Bidding

1. Ulang clone source branch dari environment yang punya akses GitHub.
2. Petakan class aktual dan dependency graph (controller -> service -> repository).
3. Pindahkan logic write-side + unit test ke repo ini.
4. Tambahkan transactional outbox dan idempotency guard.
5. Validasi kontrak dengan auth/listing/wallet menggunakan contract test.
