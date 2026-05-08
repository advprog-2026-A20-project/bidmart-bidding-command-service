# BidMart Bidding Command Service

Repository ini adalah target bounded context untuk bidding command BidMart. Service ini akan diekstrak dari `AuctionService` pada gateway legacy dengan pola strangler, sehingga flow lama tetap berjalan sampai contract wallet, listing, dan auth cukup stabil.

## Service Boundary

Bidding command service idealnya menangani:

- Place bid.
- Validasi minimum increment.
- Validasi auction masih aktif.
- Auction lifecycle untuk command side.
- Anti-sniping extension.
- Penentuan bid tertinggi.
- Winner determination.
- Publish event setelah bid berhasil atau auction selesai.

Service ini tidak menangani read-heavy endpoint auction list/detail/bid history. Query tersebut menjadi tanggung jawab `bidmart-auction-query-service`.

## Status Migrasi

Saat ini implementasi bidding masih berada di `bidmart-gateway` legacy monolith. Repo ini berisi scaffold, kontrak event, dan boundary docs sebagai langkah awal sebelum logic dipindahkan.

## Endpoint Target

```txt
POST /api/auctions
POST /api/auctions/{auctionId}/activate
POST /api/auctions/{auctionId}/bids
POST /api/auctions/{auctionId}/close
GET /actuator/health
```

## Run Lokal

```bash
./gradlew bootRun
```

Default port:

```text
8085
```

## Test

```bash
./gradlew test
```

## Dependency Service Lain

- Auth/User service untuk user id, role, dan seller/buyer validation.
- Listing service untuk validasi listing aktif dan seller ownership.
- Wallet service untuk hold, release, dan capture dana.
- Auction query service menerima projection/event dari command side.
- Gateway meneruskan command endpoint publik.

## Catatan Kritis

Jangan memindahkan bidding command sebelum expired auction closure tidak lagi bergantung pada GET query. Closure harus command-side agar wallet capture/release dan event publish tetap terjadi.
