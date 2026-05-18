# Bidding Command Boundary

## In-Scope (Command/Write Side)

- Place bid command.
- Starting price and current highest bid validation.
- Auction lifecycle command (activate/close/cancel).
- Winner state transition for command-side bids.
- Wallet orchestration through `WalletClient`: hold/release/capture.
- Event publish (`BidPlaced`, `AuctionExtended`, `AuctionClosed`, `WinnerDetermined`, `AuctionUnsold`).

## Out-of-Scope (Query/Read Side)

- `GET /api/auctions`
- `GET /api/auctions/{auctionId}`
- `GET /api/auctions/{auctionId}/bids`
- Analytics/statistik query

Endpoint query dimiliki `bidmart-auction-query-service`.

## Command API Minimal

- `POST /api/auctions`
- `POST /api/auctions/{auctionId}/activate`
- `POST /api/auctions/{auctionId}/bids`
- `POST /api/auctions/{auctionId}/close`

Frontend memanggil endpoint ini melalui gateway, bukan langsung ke service.

## Dependency Direction

```text
Gateway -> Bidding Command Service -> Wallet Service
```

## Coupling Rules

- Auth/user identity boleh berasal dari JWT/API boundary, bukan direct user repository/entity coupling.
- Listing validation boleh berasal dari API atau command-side listing snapshot, bukan direct listing repository/entity coupling.
- Wallet access harus melalui `WalletClient`, bukan dipanggil dari controller dan bukan melalui shared wallet entity/repository.
- Query/read model auction dimiliki `bidmart-auction-query-service`; command service tidak menambahkan endpoint read-heavy.

## Command State

Service ini memiliki command-side `auction` dan `bid` state. `Auction` menyimpan ID boundary
seperti `sellerId` dan `listingId`; `Bid` menyimpan `bidderId`, amount, dan status command-side.
