# Bidding Command Boundary

## Tetap di Command Service

- Place bid.
- Minimum increment validation.
- Auction lifecycle dan status transition.
- Anti-sniping extension.
- Winner determination.
- Wallet hold/release/capture orchestration.
- Domain event/outbox untuk bid dan auction lifecycle.

## Tidak Masuk Command Service

- `GET /api/auctions`
- `GET /api/auctions/{auctionId}`
- `GET /api/auctions/{auctionId}/bids`

Endpoint query tersebut dimiliki oleh `bidmart-auction-query-service`.

## Adapter yang Dibutuhkan

- `UserClient` untuk role dan identity.
- `ListingClient` untuk listing active/seller validation.
- `WalletClient` untuk hold/release/capture.
- `AuctionEventPublisher` untuk outbox atau broker.

## Risiko Utama

- Race condition bid bersamaan.
- Double hold wallet saat retry.
- Auction query menampilkan status selesai tanpa command-side wallet settlement.
- Event gagal publish setelah bid tersimpan.
