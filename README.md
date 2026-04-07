# Hotel Booking Platform

> A production-ready hotel booking backend built with **Spring Boot**, **PostgreSQL**, **Stripe**, and a **Decorator-pattern dynamic pricing engine**. Inspired by Airbnb/booking platforms.

Implements complete hotel management, per-day room inventory tracking, a 4-layer dynamic pricing engine, full Stripe payment lifecycle (create → confirm → refund), RBAC with Spring Security, and automated hourly price recalculation via a scheduled job.

---

## Table of Contents

- [System Architecture](#system-architecture)
- [Core Domain Model](#core-domain-model)
- [How It All Works — Key Flows](#how-it-all-works--key-flows)
- [Dynamic Pricing Engine](#dynamic-pricing-engine)
- [Inventory Management & Concurrency](#inventory-management--concurrency)
- [Stripe Payment Lifecycle](#stripe-payment-lifecycle)
- [Security & RBAC](#security--rbac)
- [Scheduled Jobs](#scheduled-jobs)
- [Tech Stack](#tech-stack)
- [Local Setup](#local-setup)
- [Full API Reference](#full-api-reference)
- [Project Structure](#project-structure)

---

## System Architecture

```
                    ┌─────────────────────────────────────────┐
                    │            CLIENT (Browser/App)         │
                    └──────────────┬──────────────────────────┘
                                   │ HTTP
                                   ▼
                    ┌─────────────────────────────────────────┐
                    │       Spring Boot App  :8080            │
                    │       Context Path: /api/v1             │
                    │                                         │
                    │  ┌──────────────────────────────────┐   │
                    │  │   Spring Security Filter Chain   │   │
                    │  │   JwtAuthFilter (stateless)      │   │
                    │  │   Role-based access control      │   │
                    │  └──────────────┬───────────────────┘   │
                    │                 │                       │
                    │  ┌──────────────▼───────────────────┐   │
                    │  │          Controllers             │   │
                    │  │  AuthController                  │   │
                    │  │  HotelBrowseController           │   │
                    │  │  HotelBookingController          │   │
                    │  │  HotelController (admin)         │   │
                    │  │  RoomAdminController (admin)     │   │
                    │  │  InventoryController (admin)     │   │
                    │  │  WebhookController (Stripe)      │   │
                    │  │  UserController                  │   │
                    │  └──────────────┬───────────────────┘   │
                    │                 │                       │
                    │  ┌──────────────▼───────────────────┐   │
                    │  │           Services               │   │
                    │  │  BookingServiceImpl              │   │
                    │  │  CheckOutServiceImpl → Stripe    │   │
                    │  │  InventoryServiceImpl            │   │
                    │  │  PricingUpdateService (cron)     │   │
                    │  │  PricingService (decorator)      │   │
                    │  │  HotelServiceImpl                │   │
                    │  │  RoomServiceImpl                 │   │
                    │  └──────────────┬───────────────────┘   │
                    │                 │                       │
                    │  ┌──────────────▼───────────────────┐   │
                    │  │         Repositories             │   │
                    │  │  BookingRepository               │   │
                    │  │  InventoryRepository             │   │
                    │  │  HotelMinPriceRepository         │   │
                    │  │  RoomRepository                  │   │
                    │  │  UserRepository                  │   │
                    │  └──────────────┬───────────────────┘   │
                    │                 │                       │
                    └─────────────────┼───────────────────────┘
                                      │
                    ┌─────────────────▼────────────────────────┐
                    │          PostgreSQL Database             │
                    │  Tables: user, hotel, room, inventory,   │
                    │  booking, guest, booking_guest,          │
                    │  hotel_min_price                         │
                    └──────────────────────────────────────────┘

                    ┌──────────────────────────────────────────┐
                    │            Stripe (External)             │
                    │  Checkout Session → Payment Intent       │
                    │  Webhook → checkout.session.completed    │
                    │  Refund API (on cancellation)            │
                    └──────────────────────────────────────────┘
```

---

## Core Domain Model

```
┌─────────────┐         ┌─────────────────┐         ┌──────────────────┐
│    User     │         │     Hotel       │         │      Room        │
│─────────────│         │─────────────────│         │──────────────────│
│ id          │    owns │ id              │  has    │ id               │
│ name        │◄────────│ name            │────────►│ type             │
│ email       │         │ city            │         │ basePrice        │
│ password    │         │ active (bool)   │         │ totalCount       │
│ role        │         │ contactInfo     │         │ capacity         │
│ GUEST       │         │  (embedded)     │         │ amenities[]      │
│ HOTEL_MGR   │         │ owner (User)    │         │ photos[]         │
└──────┬──────┘         └────────┬────────┘         └────────┬─────────┘
       │                         │                           │
       │                         │ 1 hotel × 1 room × 1 date │
       │                         ▼                           ▼
       │               ┌──────────────────────────────────────────┐
       │               │              Inventory                   │
       │               │──────────────────────────────────────────│
       │               │ id                                       │
       │               │ hotel_id, room_id                        │
       │               │ date          ← one row per day          │
       │               │ totalCount    ← total rooms available    │
       │               │ bookedCount   ← confirmed bookings       │
       │               │ reservedCount ← in-progress (10 min TTL) │
       │               │ surgeFactor   ← admin-set multiplier     │
       │               │ price         ← dynamically computed     │
       │               │ city          ← denormalized for search  │
       │               │ closed        ← admin can block a date   │
       │               └──────────────────────────────────────────┘
       │
       │ makes
       ▼
┌──────────────────────────────────┐        ┌──────────────────┐
│            Booking               │  has   │      Guest       │
│──────────────────────────────────│◄───────│──────────────────│
│ id                               │  many  │ id               │
│ hotel, room, user                │        │ name             │
│ checkInDate, checkOutDate        │        │ age              │
│ roomsCount                       │        │ gender           │
│ amount (BigDecimal)              │        └──────────────────┘
│ paymentSessionId (Stripe)        │
│ bookingStatus:                   │
│   RESERVED → GUESTS_ADDED        │
│   → PAYMENTS_PENDING → CONFIRMED │
│   → CANCELLED                    │
└──────────────────────────────────┘

┌──────────────────────────────────┐
│          HotelMinPrice           │  ← Pre-computed min price per hotel per day
│──────────────────────────────────│     Updated hourly by scheduled job
│ hotel, date, price               │     Used for fast hotel search queries
└──────────────────────────────────┘
```

---

## How It All Works — Key Flows

### Flow 1: Hotel Search
```
Client → GET /api/v1/hotels/search
  Body: { city, startDate, endDate, roomsCount, page, size }

  → InventoryServiceImpl.searchHotels()
      → Queries HotelMinPriceRepository (pre-computed min prices table)
        SQL logic:
          - Filter by city
          - Filter dates between startDate and endDate
          - Ensure (totalCount - bookedCount) >= roomsCount for EVERY date in range
          - GROUP BY hotel, HAVING COUNT(dates) = requested date range length
          - Returns paginated list of hotels with their minimum price
      ← Page<HotelPriceResponseDto> { hotelId, name, city, price, ... }
```

**Why `HotelMinPrice` table?** Computing dynamic prices at search time across all hotels and dates is O(hotels × dates × pricing layers). The `PricingUpdateService` pre-computes this hourly and stores min prices in a dedicated table. Search becomes a simple indexed query.

---

### Flow 2: Complete Booking Flow (5 steps)

```
STEP 1 — Initialise Booking
  Client → POST /api/v1/bookings/init
  Body: { hotelId, roomId, checkInDate, checkOutDate, roomsCount }

  → BookingServiceImpl.initialiseBooking()
      1. Verify hotel and room exist
      2. Query inventory with PESSIMISTIC_WRITE lock:
            findAndLockAvailableInventory(roomId, startDate, endDate, roomsCount)
            → Locks rows to prevent double-booking
      3. Verify inventory rows count == number of days requested
            (if not → "Room not available")
      4. initBooking() → UPDATE inventory SET reservedCount += roomsCount
            (room is now "soft-reserved" for 10 minutes)
      5. Calculate price: PricingService.calculateTotalPrice(inventoryList)
            × roomsCount
      6. Save Booking { status: RESERVED, amount, ... }
  ← BookingDto

STEP 2 — Add Guests
  Client → POST /api/v1/bookings/{bookingId}/addGuests
  Body: [guestId1, guestId2, ...]

  → Validates booking belongs to current user
  → Validates booking not expired (createdAt + 10 minutes)
  → Validates booking status == RESERVED
  → Links Guest entities to booking
  → Status: GUESTS_ADDED
  ← BookingDto

STEP 3 — Initiate Payment
  Client → POST /api/v1/bookings/{bookingId}/payments

  → CheckOutServiceImpl.getCheckoutSession()
      1. Create Stripe Customer { name, email }
      2. Create Stripe Checkout Session:
           mode: PAYMENT
           currency: INR
           amount: booking.amount × 100 (paise)
           product: "HotelName : RoomType"
           successUrl + cancelUrl → frontend/payments/{bookingId}/status
      3. Save session.getId() as booking.paymentSessionId
      4. Status: PAYMENTS_PENDING
  ← { sessionUrl } → client redirects user to Stripe-hosted checkout page

STEP 4 — Stripe Webhook (payment confirmed)
  Stripe → POST /api/v1/webhook/payment
  Header: Stripe-Signature (verified against webhook secret)

  → BookingServiceImpl.capturePayment(event)
      if event.type == "checkout.session.completed":
        1. Verify Stripe signature → construct Event object
        2. Find booking by paymentSessionId
        3. Status: CONFIRMED
        4. Lock inventory with PESSIMISTIC_WRITE
        5. confirmBooking():
              UPDATE inventory SET
                reservedCount -= roomsCount,
                bookedCount += roomsCount
        6. Save booking

STEP 5 — Check Status (optional polling)
  Client → GET /api/v1/bookings/{bookingId}/status
  ← { bookingStatus: "CONFIRMED" / "PAYMENTS_PENDING" / ... }
```

---

### Flow 3: Cancel Booking with Automatic Refund
```
Client → POST /api/v1/bookings/{bookingId}/cancel

→ Validates booking belongs to current user
→ Validates bookingStatus == CONFIRMED (only confirmed can cancel)
→ Status: CANCELLED
→ cancelBooking():
      UPDATE inventory SET bookedCount -= roomsCount
      (releases rooms back to available pool)
→ Stripe refund:
      Session.retrieve(paymentSessionId)
      → get PaymentIntent
      → Refund.create(paymentIntentId)
      → Full refund triggered automatically
← 204 No Content
```

---

### Flow 4: Admin Creates Hotel + Rooms
```
Admin (HOTEL_MANAGER role) → POST /api/v1/admin/hotels
  → Creates hotel, sets owner = current user

Admin → POST /api/v1/admin/hotels/{hotelId}/rooms
  → Creates room with basePrice, type, capacity, totalCount
  → InventoryServiceImpl.initializeRoomForAYear(room):
        Loops from today → today + 1 year
        Creates one Inventory row per day:
          { totalCount, bookedCount:0, reservedCount:0,
            price: basePrice, surgeFactor: 1.0, closed: false }
        → 365 rows created per room

Admin → PATCH /api/v1/admin/hotels/{hotelId}/activate
  → Hotel becomes publicly searchable
```

---

## Dynamic Pricing Engine

Built using the **Decorator design pattern** — each pricing rule wraps the previous, applying its multiplier on top of the already-computed price.

```
                 ┌─────────────────────────────────────────────────────────┐
                 │                  PricingService                         │
                 │  calculateDynamicPricing(inventory):                    │
                 │                                                         │
                 │  BasePricingStrategy          → room.basePrice          │
                 │       ↑ wrapped by                                      │
                 │  SurgePricingStrategy         → × inventory.surgeFactor │
                 │       ↑ wrapped by                                      │
                 │  OccupancyPricingStrategy     → × 1.2 if occupancy >80% │
                 │       ↑ wrapped by                                      │
                 │  UrgencyPricingStrategy       → × 1.15 if date < 7 days │
                 │       ↑ wrapped by                                      │
                 │  HolidayPricingStrategy       → × 1.25 if holiday       │
                 │                                                         │
                 │  Final Price = basePrice × surgeFactor                  │
                 │               × occupancyMultiplier                     │
                 │               × urgencyMultiplier                       │
                 │               × holidayMultiplier                       │
                 └─────────────────────────────────────────────────────────┘
```

### Each Strategy Explained

| Strategy | Rule | Multiplier |
|---|---|---|
| `BasePricingStrategy` | Returns `room.basePrice` — the floor price | 1× (base) |
| `SurgePricingStrategy` | Applies `inventory.surgeFactor` set by hotel admin | Variable (admin-controlled) |
| `OccupancyPricingStrategy` | If `bookedCount / totalCount > 0.8` (>80% full) | 1.2× |
| `UrgencyPricingStrategy` | If check-in date is within 7 days from today | 1.15× |
| `HolidayPricingStrategy` | If the date is a holiday | 1.25× |

**Example price calculation:**
```
basePrice = ₹2000
surgeFactor = 1.5 (admin set during a festival)
occupancy = 85% → triggers 1.2×
urgency = booking for tomorrow → triggers 1.15×
holiday = true → triggers 1.25×

Final = 2000 × 1.5 × 1.2 × 1.15 × 1.25 = ₹5,175
```

**Why Decorator pattern?** Each pricing rule is independently testable and extendable. Adding a new rule (e.g. `WeekendPricingStrategy`) requires zero changes to existing code — just wrap with a new decorator.

---

## Inventory Management & Concurrency

### Inventory Table Structure
Every room has one `Inventory` row per date for the next 365 days:

```
room_id | date       | totalCount | bookedCount | reservedCount | price  | closed
--------|------------|------------|-------------|---------------|--------|-------
1       | 2026-04-07 | 10         | 6           | 2             | 3200   | false
1       | 2026-04-08 | 10         | 3           | 0             | 2800   | false
1       | 2026-04-09 | 10         | 10          | 0             | 4100   | true  ← admin blocked
```

### Three-Count System
- `totalCount` — physical rooms in hotel
- `bookedCount` — payment confirmed, room locked
- `reservedCount` — booking initiated but payment not yet done (10-min TTL)

Available rooms = `totalCount - bookedCount - reservedCount`

### Preventing Double-Booking: Pessimistic Locking
When two users try to book the same room on the same date simultaneously:

```
User A initiates booking               User B initiates booking
         │                                      │
         ▼                                      ▼
findAndLockAvailableInventory()        findAndLockAvailableInventory()
  SELECT ... FOR UPDATE (locks rows)   → BLOCKED — waits for User A's lock
         │
   reservedCount += 1
   COMMIT transaction
         │
         └── Lock released
                                              │
                                        Gets lock, re-reads inventory
                                        → If available: reserves for User B
                                        → If full: throws "Room not available"
```

`@Lock(LockModeType.PESSIMISTIC_WRITE)` on the JPQL query ensures row-level locking at the database level — no race conditions possible.

### Booking Expiry
If a user initiates a booking but doesn't complete payment within 10 minutes:
```java
booking.getCreatedAt().plusMinutes(10).isBefore(LocalDateTime.now())
```
The next attempt (add guests / initiate payment) throws `IllegalStateException`. The `reservedCount` is never decremented automatically — this is a known limitation for a future cleanup job.

---

## Stripe Payment Lifecycle

```
┌─────────┐    POST /bookings/{id}/payments     ┌──────────────────┐
│  Client │ ──────────────────────────────────► │  Spring Boot App │
└─────────┘                                     └────────┬─────────┘
                                                         │
                                          Creates Stripe Checkout Session
                                          (Customer + LineItem + URLs)
                                                         │
                                                         ▼
                                                ┌─────────────────┐
                                                │  Stripe API     │
                                                │  Returns        │
                                                │  session.url    │
                                                └────────┬────────┘
                                                         │
┌─────────┐ ◄── sessionUrl ────────────────────────────┘
│  Client │  Redirects user to Stripe-hosted payment page
└────┬────┘
     │  User enters card details on Stripe
     │
     ▼
┌──────────┐  checkout.session.completed webhook    `┌──────────────────┐
│  Stripe  │ ──────────────────────────────────────► │  /webhook/payment│
└──────────┘  (Stripe-Signature header verified)    `└────────┬─────────┘
                                                              │
                                                   capturePayment(event)
                                                   → status: CONFIRMED
                                                   → inventory: confirmed
                                                               │
┌─────────┐  GET /bookings/{id}/status   ◄─────────────────────┘
│  Client │  polls for CONFIRMED status
└─────────┘
```

**On cancellation:** `Session.retrieve(sessionId)` → get `paymentIntentId` → `Refund.create()` → automatic full refund.

---

## Security & RBAC

```
┌───────────────────────────────────────────────────────────────┐
│                    Spring Security Config                     │
│                                                               │
│  /admin/**           → ROLE_HOTEL_MANAGER only                │
│  /bookings/**        → Any authenticated user                 │
│  /users/**           → Any authenticated user                 │
│  /hotels/**          → Public (no auth)                       │
│  /webhook/**         → Public (Stripe signature verified)     │
│  /auth/**            → Public                                 │
│                                                               │
│  Session policy: STATELESS (no server-side sessions)          │
│  Auth method: JWT Bearer token in Authorization header        │
└───────────────────────────────────────────────────────────────┘
```

**JWT Flow:**
1. `POST /api/v1/auth/login` → validates credentials → returns signed JWT
2. Every subsequent request includes `Authorization: Bearer <token>`
3. `JwtAuthFilter` intercepts, validates token, sets `SecurityContext`
4. `AppUtils.getCurrentUser()` reads from `SecurityContext` in any service

**Ownership checks** (beyond role-based):
- `BookingService` verifies `booking.getUser().equals(currentUser)` before any action
- `HotelService` verifies `hotel.getOwner().equals(currentUser)` for admin operations
- `InventoryService` verifies hotel ownership before allowing updates

---

## Scheduled Jobs

### `PricingUpdateService` — Runs Every Hour
```
@Scheduled(cron = "0 0 * * * *")  // every hour
updatePrices():
  1. Fetch all hotels in batches of 100 (pagination to avoid OOM)
  2. For each hotel:
     a. Get all inventory rows from today → today+1year
     b. For each inventory row:
          dynamicPrice = PricingService.calculateDynamicPricing(inventory)
          inventory.setPrice(dynamicPrice)
     c. Bulk save all updated inventory rows
     d. Compute minimum price per date across all rooms in hotel
     e. Upsert HotelMinPrice rows (used for fast hotel search)
```

**Why this matters:** Without this job, the `HotelMinPrice` table (used for search) would show stale prices. The job ensures search results always reflect current dynamic pricing including urgency (< 7 days) and occupancy changes.

---

## Tech Stack

| Layer | Technology | Purpose |
|---|---|---|
| Framework | Spring Boot 3 | Core application framework |
| Security | Spring Security + JWT | Stateless auth, RBAC |
| ORM | Spring Data JPA + Hibernate | Database access |
| Database | PostgreSQL | Primary data store |
| Payments | Stripe API (Checkout + Webhooks + Refunds) | Full payment lifecycle |
| Pricing | Decorator Pattern (custom) | Layered dynamic pricing |
| Concurrency | JPA Pessimistic Locking | Prevent double-booking |
| Scheduling | Spring `@Scheduled` | Hourly price recalculation |
| API Docs | Swagger / OpenAPI 3 | `@Operation` annotations on all endpoints |
| Mapping | ModelMapper | Entity ↔ DTO conversion |
| Build | Maven | Dependency management |

---

## Local Setup

### Prerequisites
- Java 17+
- PostgreSQL running on `localhost:5432`
- Stripe account (test mode keys)
- Stripe CLI (for local webhook testing)

### Database Setup
```sql
CREATE DATABASE APPCLONE;
```
Spring JPA will auto-create all tables on startup (`ddl-auto: update`).

### Environment Variables
```bash
DB_PASSWORD=your_postgres_password
JWT_SECRET=your_jwt_secret_min_32_chars
STRIPE_SECRET_KEY=sk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
```

### Run the Application
```bash
mvn clean install
mvn spring-boot:run
```
App starts at: `http://localhost:8080/api/v1`

### Stripe Webhook (local testing)
```bash
stripe listen --forward-to localhost:8080/api/v1/webhook/payment
```
This forwards Stripe events to your local server. Copy the `whsec_...` key printed and set it as `STRIPE_WEBHOOK_SECRET`.

---

## Full API Reference

Base URL: `http://localhost:8080/api/v1`

### Authentication (Public)

| Method | Endpoint | Body | Response |
|---|---|---|---|
| `POST` | `/auth/signup` | `{ name, email, password }` | `UserDto` + 201 |
| `POST` | `/auth/login` | `{ email, password }` | JWT string + 200 |

### Hotel Search (Public)

| Method | Endpoint | Body | Response |
|---|---|---|---|
| `GET` | `/hotels/search` | `{ city, startDate, endDate, roomsCount, page, size }` | `Page<HotelPriceResponseDto>` |
| `GET` | `/hotels/{hotelId}/info` | `{ startDate, endDate, roomsCount }` | `HotelInfoDto` with room prices |

### Booking Flow 🔒 (Authenticated)

| Method | Endpoint | Body | Description |
|---|---|---|---|
| `POST` | `/bookings/init` | `{ hotelId, roomId, checkInDate, checkOutDate, roomsCount }` | Step 1: Reserve rooms |
| `POST` | `/bookings/{id}/addGuests` | `[guestId1, guestId2]` | Step 2: Add guests |
| `POST` | `/bookings/{id}/payments` | — | Step 3: Get Stripe checkout URL |
| `GET` | `/bookings/{id}/status` | — | Poll booking status |
| `POST` | `/bookings/{id}/cancel` | — | Cancel + auto refund |
| `GET` | `/bookings/my` | — | Get all my bookings |

### Admin — Hotel Management 🔒 (HOTEL_MANAGER role)

| Method | Endpoint | Body | Description |
|---|---|---|---|
| `POST` | `/admin/hotels` | `{ name, city, ... }` | Create hotel |
| `GET` | `/admin/hotels` | — | Get my hotels |
| `PUT` | `/admin/hotels/{id}` | `HotelDto` | Update hotel |
| `DELETE` | `/admin/hotels/{id}` | — | Delete hotel |
| `PATCH` | `/admin/hotels/{id}/activate` | — | Make hotel publicly visible |
| `GET` | `/admin/hotels/{id}/bookings` | — | View all bookings |
| `GET` | `/admin/hotels/{id}/report` | `{ startDate, endDate }` | Revenue report |

### Admin — Room Management 🔒 (HOTEL_MANAGER role)

| Method | Endpoint | Body | Description |
|---|---|---|---|
| `POST` | `/admin/hotels/{hotelId}/rooms` | `{ type, basePrice, totalCount, capacity, amenities }` | Create room + auto-init 365 inventory rows |
| `GET` | `/admin/hotels/{hotelId}/rooms` | — | List rooms |
| `PUT` | `/admin/hotels/{hotelId}/rooms/{roomId}` | `RoomDto` | Update room |
| `DELETE` | `/admin/hotels/{hotelId}/rooms/{roomId}` | — | Delete room + inventory |

### Admin — Inventory Management 🔒 (HOTEL_MANAGER role)

| Method | Endpoint | Body | Description |
|---|---|---|---|
| `GET` | `/admin/inventory/rooms/{roomId}` | — | View all inventory by room |
| `PATCH` | `/admin/inventory/rooms/{roomId}` | `{ startDate, endDate, closed, surgeFactor }` | Update inventory for date range |

### Stripe Webhook (Public, signature-verified)

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/webhook/payment` | Receives `checkout.session.completed` from Stripe |

### User Profile 🔒 (Authenticated)

| Method | Endpoint | Body | Description |
|---|---|---|---|
| `GET` | `/users/profile` | — | Get my profile |
| `PUT` | `/users/profile` | `ProfileUpdateRequestDto` | Update profile |

---

## Project Structure

```
AirBnbApp/
└── src/main/java/com/airBnb/application/AirBnbApp/
    │
    ├── controller/
    │   ├── AuthController.java              ← signup, login
    │   ├── HotelBrowseController.java       ← public hotel search
    │   ├── HotelBookingController.java      ← booking lifecycle
    │   ├── HotelController.java             ← admin hotel CRUD
    │   ├── RoomAdminController.java         ← admin room CRUD
    │   ├── InventoryController.java         ← admin inventory mgmt
    │   ├── WebhookController.java           ← Stripe webhook receiver
    │   └── UserController.java              ← user profile
    │
    ├── service/
    │   ├── BookingServiceImpl.java          ← core booking logic + Stripe
    │   ├── CheckOutServiceImpl.java         ← Stripe session creation
    │   ├── InventoryServiceImpl.java        ← search + inventory CRUD
    │   ├── PricingUpdateService.java        ← hourly scheduled job
    │   ├── HotelServiceImpl.java            ← hotel CRUD + reports
    │   ├── RoomServiceImpl.java             ← room CRUD + inventory init
    │   ├── GuestServiceImpl.java            ← guest management
    │   └── UserServiceImpl.java             ← profile management
    │
    ├── strategy/                            ← Dynamic Pricing (Decorator)
    │   ├── PricingStrategy.java             ← interface
    │   ├── PricingService.java              ← chains decorators, calculates total
    │   ├── BasePricingStrategy.java         ← returns room.basePrice
    │   ├── SurgePricingStrategy.java        ← × surgeFactor
    │   ├── OccupancyPricingStrategy.java    ← × 1.2 if >80% full
    │   ├── UrgencyPricingStrategy.java      ← × 1.15 if <7 days away
    │   └── HolidayPricingStrategy.java      ← × 1.25 if holiday
    │
    ├── entity/
    │   ├── User.java
    │   ├── Hotel.java
    │   ├── Room.java
    │   ├── Inventory.java                   ← per-day availability + pricing
    │   ├── Booking.java                     ← full booking with status machine
    │   ├── Guest.java
    │   ├── HotelContactInfo.java            ← @Embeddable
    │   ├── HotelMinPrice.java               ← pre-computed for search
    │   └── enums/
    │       ├── BookingStatus.java           ← RESERVED → CONFIRMED → CANCELLED
    │       ├── PaymentStatus.java
    │       ├── Role.java                    ← GUEST, HOTEL_MANAGER
    │       └── Gender.java
    │
    ├── repository/
    │   ├── InventoryRepository.java         ← JPQL with pessimistic locks
    │   ├── BookingRepository.java
    │   ├── HotelMinPriceRepository.java     ← paginated hotel search
    │   ├── HotelRepository.java
    │   ├── RoomRepository.java
    │   ├── GuestRepository.java
    │   └── UserRepository.java
    │
    ├── security/
    │   ├── WebSecurityConfig.java           ← route-level RBAC
    │   ├── JwtAuthFilter.java               ← JWT validation per request
    │   ├── JwtService.java                  ← token generation + parsing
    │   └── AuthService.java                 ← UserDetailsService impl
    │
    ├── advices/
    │   ├── GlobalExceptionHandler.java      ← @ControllerAdvice
    │   ├── GlobalResponseHandler.java       ← wraps all responses in ApiResponse
    │   ├── ApiResponse.java                 ← { success, data, error }
    │   └── ApiError.java                    ← { message, subErrors }
    │
    └── config/
        ├── StripeConfig.java                ← sets Stripe.apiKey on startup
        ├── MapperConfig.java                ← ModelMapper bean
        └── CorsConfig.java                  ← CORS for frontend
```
