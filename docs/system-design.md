# MALRO — System Design (Layer 1)

**Status:** Draft for product-owner review  
**Date:** 2026-11-11  
**Decision baseline:** One Seller = One Organization = One Shop

## 1. Product purpose and scope

MALRO is a Barmer-first, multi-vendor local-commerce marketplace. It connects customers with approved local shops and coordinates order fulfilment through sellers and delivery partners. The platform has four operational roles: Customer, Seller, Delivery Partner, and Platform Admin.

The current product is being evolved from an existing repository and deployed services. This document records the system-level decisions that should guide later architecture, database, authorization, API, frontend, testing, and operations layers. It does not authorize a stack replacement or an immediate database migration.

### Initial launch boundary

- Initial geographic focus: Barmer, Rajasthan.
- Product shape: one customer order is placed with one shop at a time.
- Seller onboarding is approval-based.
- Admin is a platform-level operational role, not a shop tenant.
- Existing Android and web clients remain in scope according to the repository's branch ownership and documented stack.

Population, order-volume, concurrency, availability, and growth targets have not yet been supplied. They remain open inputs; no capacity target is assumed here.

## 2. Actors and responsibilities

### Customer

- Discovers shops and their listed products.
- Provides or confirms a delivery location and required contact details at checkout.
- Places and tracks an order with one shop at a time.
- Receives order-status and delivery updates.
- Can access only their own profile, addresses, and orders.

### Seller

- Applies to join the marketplace by submitting shop and owner details.
- Sees a pending application state until Platform Admin reviews it.
- After approval, receives a unique Seller ID and completes the agreed credential setup.
- Manages only the shop, catalogue, inventory/availability, and orders belonging to that seller's organization.
- Updates order preparation and hand-off status within permitted workflow transitions.

### Delivery Partner

- Applies to join and waits for Platform Admin approval.
- After approval, receives a unique Delivery Partner ID and completes the agreed credential setup.
- Sees only delivery tasks assigned to them.
- Shares live location only while the relevant delivery/presence feature is active and permitted.
- Updates pickup and delivery progress for assigned tasks only.

A delivery partner is a platform-level actor by default, not a permanent member of one seller organization. A future explicit shop-affiliation feature would require a separate product decision and authorization model.

### Platform Admin

- Reviews and approves/rejects seller and delivery-partner applications.
- Manages platform-level seller/shop and delivery-partner records.
- Assigns delivery partners to eligible orders.
- Handles operational oversight and platform-level support.
- Has privileged access that must be explicitly authorized, audited where appropriate, and not implemented as an ordinary shop membership.

## 3. Tenant model — approved decision

**One Seller = One Organization = One Shop.**

For MALRO, the tenant boundary is the Seller Organization. Each approved seller organization owns exactly one shop. There is no supported multi-shop seller organization in this product model.

Consequences:

1. Each approved seller maps to exactly one organization.
2. Each organization maps to exactly one shop.
3. A shop's catalogue, inventory/availability, seller-managed settings, and shop orders belong to that shop's organization.
4. Seller access is restricted to the seller's own organization and its one shop.
5. A seller cannot read or modify another seller's shop data by changing an identifier in a request.
6. Customer and Delivery Partner identities are platform-wide identities. Their access to shop-related records is limited to the specific customer-owned or delivery-assigned records needed for a workflow.
7. Platform Admin permissions are separate from tenant membership and must be granted through platform-level authorization.
8. Seller onboarding applications are pre-tenant records. An organization/shop relationship is established only after approval; pending applicants must not gain seller access to shop data.

### Data ownership classification

| Data domain | Ownership / boundary |
|---|---|
| Seller application | Platform onboarding; not yet an active tenant |
| Seller organization | One approved seller; one-to-one with its shop |
| Shop profile and catalogue | The owning seller organization |
| Shop inventory/availability | The owning seller organization |
| Order | One shop only; linked to the owning organization/shop and customer |
| Customer profile and addresses | Customer-owned, platform-wide |
| Delivery partner profile | Platform-wide; not owned by a shop by default |
| Delivery assignment | Platform workflow linking one order to an assigned delivery partner |
| Live delivery location | Delivery-partner operational data; purpose- and time-limited, exposed only to authorized workflow participants |
| Admin actions | Platform-level; never inferred from shop ownership |

The exact physical database representation (for example, where an `organization_id` is stored versus derived through a shop relationship) is a Layer 3 decision. Do not add a redundant tenant column to every table without reviewing the current schema, foreign keys, RLS policies, and migration history. The logical isolation requirement is mandatory regardless of the eventual column layout.

## 4. Core business workflows

### 4.1 Seller onboarding

1. Seller submits shop and owner details.
2. Application is stored with pending status.
3. Platform Admin reviews the application.
4. Admin approves or rejects it.
5. On approval, the platform establishes the seller's unique identity and exactly one organization/shop relationship.
6. Seller completes the agreed credential setup and can access only their own shop.
7. Rejected or pending applicants do not receive active seller permissions.

The exact approval fields, unique-ID format, password lifecycle, and recovery process remain implementation details for later layers.

### 4.2 Customer ordering

1. Customer browses shops and products.
2. Customer selects products from one shop.
3. Customer confirms the delivery location and required contact information.
4. Backend validates product availability, quantities, prices, shop ownership, and order rules.
5. Backend creates the order for that single shop and calculates authoritative totals.
6. Customer can view only their own order and its permitted status.

The client must not be the source of truth for prices, totals, payment state, or authorization.

### 4.3 Seller fulfilment

1. Seller receives an eligible order for their own shop.
2. Seller accepts or processes it according to the supported order state machine.
3. Seller prepares and marks the order ready for hand-off.
4. Seller cannot update orders belonging to another shop.

### 4.4 Admin assignment and delivery

1. Platform Admin selects an eligible order and an approved delivery partner.
2. Backend records the assignment and enforces that the order is assignable.
3. Assigned delivery partner sees the task.
4. Delivery partner records pickup and delivery progress.
5. Customer receives the permitted status and delivery updates.
6. Other delivery partners cannot access or update the assignment.

### 4.5 Order lifecycle baseline

The business flow is:

`Customer places order → Seller processes/prepares → Admin assigns delivery → Delivery Partner picks up → Delivery Partner delivers → Customer sees completion`

This is a high-level flow, not the final exhaustive status enum. Exact states, cancellation/refund rules, reassignment, failed delivery, and transition permissions must be specified in the API/workflow layer before implementation changes.

## 5. Sensitive data and trust boundaries

| Data | Sensitivity | Required handling |
|---|---|---|
| Customer phone/contact details | Personal data | Return only to actors who need it for a valid order/support task |
| Customer saved addresses | Personal/location data | Customer ownership; strict access checks |
| Checkout GPS/current location | Precise location | Request only when needed; explain purpose; do not expose broadly |
| Delivery partner live GPS | Highly sensitive location data | Share only during an active, authorized delivery/presence session; minimize retention |
| Seller owner and shop details | Personal/business data | Seller can manage own record; platform admin access only as required |
| Passwords/credentials and auth tokens | Secret authentication data | Never log or store plaintext passwords; use established auth/secure credential handling |
| Order contents and amounts | Commercial/customer data | Customer, owning seller, assigned delivery partner (minimum necessary), and authorized admin |
| Admin privileges and audit events | Privileged operational data | Server-side authorization; sensitive actions should be auditable |

### Trust boundaries

- Mobile/web clients are untrusted. They may request actions but cannot decide authorization or authoritative order values.
- Supabase Auth establishes identity; database RLS and trusted backend functions enforce record-level access.
- Seller organization/shop boundaries must be enforced on the server/database, not merely by hiding UI controls.
- Delivery assignment is a narrow cross-domain relationship: it grants access only to the assigned order fields and delivery actions, not general seller data.
- Admin privilege is a separate platform trust boundary and must not be granted based on a client-supplied role or shop ID.

## 6. System-level invariants

These invariants constrain all later layers:

1. Backend/database is the source of truth for identity-linked permissions, prices, totals, order state, assignments, and approval state.
2. One approved Seller maps to one Organization, and that Organization maps to one Shop.
3. Seller data access is tenant-isolated; identifier guessing or tampering must not cross shop boundaries.
4. Customer access is limited to customer-owned records and permitted order views.
5. Delivery Partner access is limited to assigned delivery work and the minimum data needed to complete it.
6. Admin is platform-scoped and separately authorized.
7. Every state-changing operation validates input and authorization on the server.
8. RLS and backend authorization must agree; frontend route guards are not security controls.
9. Location collection and sharing are purpose-limited and minimized.
10. Money is represented in integer paise; client-submitted totals are never trusted.
11. Schema changes are migration-based and must account for the actual production schema and existing migration history.
12. Existing approved stack and branch boundaries remain in force unless the product owner explicitly approves a change.

## 7. Geographic scope, scale, and service expectations

### Known

- Initial operating focus is Barmer, Rajasthan.
- The product has Customer, Seller, Delivery Partner, and Admin experiences.
- The system includes order processing, seller catalogue management, delivery assignment, and location-related workflows.

### Not yet specified

- Number of active shops at launch and after 12 months.
- Expected daily orders and peak orders per minute.
- Concurrent customer, seller, and delivery users.
- Geographic expansion schedule beyond Barmer.
- Required uptime, recovery objectives, and support hours.
- Maximum acceptable latency for catalogue, checkout, order updates, and live location.

These values must be agreed before meaningful capacity planning, rate-limit quotas, caching policy, load-test targets, or scaling claims are made. The initial design should avoid unnecessary distributed-system complexity while preserving clear module boundaries and tenant isolation.

## 8. Explicitly out of scope for this layer

This document does not decide or implement:

- Physical database columns, indexes, constraints, RLS SQL, or migrations.
- Exact authentication provider configuration, password reset, or session policy.
- Final API routes, request/response schemas, or complete order status enum.
- Payment gateway, refunds, tax rules, commissions, or settlement design.
- Maps provider, geofencing thresholds, location sampling frequency, or retention duration.
- Redis, queues, push notification provider, Sentry, or other unapproved infrastructure.
- Numerical scale targets or service-level objectives.
- UI layouts or visual design.

Those decisions belong to their relevant later layers and require evidence or explicit product approval.

## 9. Open questions for later layers

1. What are the launch and 12-month targets for shops, orders/day, peak concurrent users, and delivery partners?
2. What is the exact customer cancellation and refund policy?
3. What are the allowed order-state transitions, including seller rejection, delivery reassignment, and failed delivery?
4. What location-sharing window and retention period are acceptable for delivery partners?
5. What admin actions require an audit trail and what is the support access policy?
6. Are shop staff accounts required, or will each seller account be the only shop operator initially?

## 10. Layer 1 acceptance checklist

- [x] Product purpose and Barmer-first scope recorded.
- [x] Four roles and their responsibilities recorded.
- [x] Tenant model fixed as One Seller = One Organization = One Shop.
- [x] Core seller, customer-order, fulfilment, and delivery workflows recorded.
- [x] Sensitive data and trust boundaries identified.
- [x] Known scale facts separated from unanswered capacity questions.
- [x] Physical schema and implementation details deferred to the appropriate later layers.
- [ ] Product owner review and approval.
