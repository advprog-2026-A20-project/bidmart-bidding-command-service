# Event Contract Bidding

Gunakan envelope seragam untuk semua event:

```json
{
  "eventId": "uuid",
  "eventType": "BidPlaced",
  "version": 1,
  "aggregateId": "auctionId",
  "aggregateVersion": 1,
  "occurredAt": "2026-05-08T10:00:00Z",
  "correlationId": "uuid",
  "data": {}
}
```

## BidPlaced

```json
{
  "auctionId": "uuid",
  "listingId": "uuid",
  "bidId": "uuid",
  "bidderId": "uuid",
  "amount": "120.00",
  "sequenceNumber": 1,
  "submittedAt": "2026-05-08T10:00:00Z",
  "previousLeaderBidId": null,
  "previousLeaderId": null,
  "currentPrice": "120.00",
  "nextMinimumBid": "130.00"
}
```

## AuctionExtended

```json
{
  "auctionId": "uuid",
  "listingId": "uuid",
  "triggerBidId": "uuid",
  "previousEndsAt": "2026-05-08T10:00:00Z",
  "newEndsAt": "2026-05-08T10:02:00Z",
  "extensionCount": 1,
  "reason": "LAST_MINUTE_BID"
}
```

## AuctionClosed

```json
{
  "auctionId": "uuid",
  "listingId": "uuid",
  "closedAt": "2026-05-08T10:02:00Z",
  "closingReason": "SCHEDULED_END",
  "highestBidId": "uuid",
  "highestBidAmount": "150.00"
}
```

## WinnerDetermined

```json
{
  "auctionId": "uuid",
  "listingId": "uuid",
  "winnerId": "uuid",
  "winningBidId": "uuid",
  "winningAmount": "150.00",
  "reservePrice": "100.00",
  "closedAt": "2026-05-08T10:02:00Z"
}
```

## AuctionUnsold

```json
{
  "auctionId": "uuid",
  "listingId": "uuid",
  "reservePrice": "200.00",
  "highestBidId": "uuid",
  "highestBidAmount": "150.00",
  "closedAt": "2026-05-08T10:02:00Z",
  "reason": "RESERVE_NOT_MET"
}
```
