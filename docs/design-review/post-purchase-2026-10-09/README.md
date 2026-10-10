# The piece's journey — October 9, 2026

This release carries the existing atelier design through the purchase aftermath:
buyers can understand what happens next, and the shop can work its actual queues.
It builds on `16c6264` and preserves the secure guest checkout and explicit account
claiming already delivered. All browser evidence uses fictional disposable Docker
QA data. No real payment, shipment or external customer email was made.

## Reproduced findings and decisions

| Finding and evidence | Affected task and change | Priority / effort / dependency | Acceptance and expected benefit |
| --- | --- | --- | --- |
| Order history only exposed a status badge; no recorded progression or carrier reference. Source review and the previous release reproduce this gap. | Understand a purchase after paying. Add one shared journey to signed-in and verified guest order views, with real nullable milestone dates and safe carrier links. | P1 / medium; backend status transitions and existing protected order access. | Pending orders do not claim payment; no invented legacy dates; cancellation has its own explanation; recorded delivery has a help route. Clearer next steps are a usability hypothesis. |
| Admin could mark an order shipped without recording a carrier reference. [Before](screenshots/admin-before.jpg). | Dispatch a paid order. Require an atomic shipment command with a carrier code and optional HTTPS link; preserve idempotent identical replay. | P1 / medium; existing paid → preparing → shipped → delivered progression. | Status-only shipping is rejected; missing carrier details are explained; the customer and shipping email receive the saved reference. |
| Admin's newest-first mixed list hid older paid work behind completed purchases. [Before](screenshots/admin-before.jpg). | Find the next parcel to prepare. Add real totals and bounded server-filtered oldest-first queues. [After](screenshots/admin-after.jpg). | P1 / medium; database queries and admin authorization. | Queue totals cover the complete database, not the visible page. Transitioning a filtered row reloads from the start, preventing skipped rows after offsets shift. Better prioritization is a usability hypothesis. |
| Purchases and preparation/shipment milestones had no durable customer notification. | Hear about a purchase without repeatedly opening the shop. Save an immutable email snapshot with the order transaction; retry delivery outside that transaction. | P1 / medium; monitored SMTP in production, private provider double in QA. | Rollbacks leave neither the transition nor its email; failed sends retain the purchase; leases prevent competing workers; outdated unpaid/preparation/shipment messages are suppressed; admins can recover failures. |
| `/contact` and `/faq` redirected to the catalog. [Before](screenshots/contact-before.jpg). | Find an existing order or get help. Provide order and guest lookup routes, keyboard-operated factual FAQs and a runtime-configured support mailbox. [After](screenshots/contact-after.jpg). | P1 / small; shop owner supplies a real monitored support address. | Both routes show help in both languages. Account creation remains optional for guest viewing. No invented promises, contact details or return policies. |
| Legacy orders can lack dates; decorative completion marks alone would not explain reached milestones to a screen reader. | Understand historical order progression using assistive technology. Add readable milestone announcements and retain textual future states. | P1 / small; shared journey component. | Reached milestones are announced even without dates. Decorative marks are hidden from the accessibility tree. |

## Design direction and shared decisions

Keep the contemporary artisan atelier already established: local Playfair display
type, local Poppins interface type, warm paper surfaces, restrained terracotta
actions and generous spacing. The expressive feature is a **piece's journey** that
connects the buyer's view, the actual shipment command and the purchase email.
The queues answer what the shop should do next using operational counts.

The journey uses a vertical list on phones and four columns from the existing
small-screen breakpoint. It shares the existing typography, colors, panels,
focus treatments and action styles; no new icon/font/image dependency was added.
Carrier details wrap on narrow screens. The help center and admin shell expose
main landmarks. Native FAQ disclosures keep their expected keyboard behavior.
Purchase emails use minimal escaped HTML plus plain text, without remote images
or tracking pixels. Their content is bilingual; protected order links keep the
existing verification and sign-in requirements.

## Implementation checklist

- [x] Shared customer/verified guest journey and actual milestone timestamps.
- [x] Carrier code required by a locked, replay-safe shipment command.
- [x] Database-backed email snapshots, bounded worker leases/retries and admin recovery.
- [x] Oldest-first operational queues with full database totals.
- [x] Factual Contact/FAQ center and optional public support mailbox setting.
- [x] English and Brazilian Portuguese translations, including localized dates.
- [x] Fixed fictional H2 history, protected purchase inbox and restart reset checks.
- [x] Production setup and additive-schema/rollback documentation.

## Browser and functional evidence

Three screens were checked at **320, 390, 768, 1280 and 1440 pixels** in both
languages: admin orders, help, and a customer's shipped order. The measurements
are in [responsive.json](responsive.json); all 30 samples had document width within
the viewport. Representative screenshots are retained for every sample in
`screenshots/`. The narrower measurements exclude the browser's scrollbar.

Keyboard Enter opened the Portuguese preparation FAQ, with a visible 2px focus
outline. Its disclosures measured 44px high; the order lookup actions measured
49px. Queue/action buttons measured at least 44px high. The journey exposes an
ordered list, named region, textual reached/future states and safe new-window link
attributes. This is a scoped browser and automated review, not a complete WCAG
certification or a screen-reader/device laboratory study.

In live QA, an empty carrier form showed its validation alert. Recording
`QA-VISUAL-123` for fictional order `df2eec99` moved the preparation queue from 7
to 6 and the carrier queue from 6 to 7. The same reference and actual new shipment
date appeared in the buyer's protected order and private purchase inbox:
[customer](screenshots/shipment-customer-after.jpg),
[inbox](screenshots/qa-purchase-inbox.jpg).

The application build passed **737 backend tests and 481 Angular tests**, both
localized production bundles, five artifact tests and both backend boundary
checks. Ten QA fixture tests passed. The existing PostgreSQL guest/idempotency
rollout rehearsal passed. The isolated Docker smoke run passed catalog/gallery
images, personalized artwork, shipping quotes, order/payment replay, PIX,
fulfillment, purchase emails, admin authorization, account recovery, guest
capabilities/verification/claiming and complete fixed-data restoration after
restart. The final frontend landmark/accessibility polish was rebuilt and its
481 tests and production artifact checks passed again.

Tests for this release specifically cover transaction/event rollback, duplicate
milestones, leases and stale completion, retry/recovery, outdated unpaid notices,
guest email privacy, shipment validation/replay, admin authorization, server queue
filtering, safe tracking links and unconfigured/invalid support mailbox settings.

## Comparable local performance

Two sequential cold mobile Home audits of the previous release and two of the
final source used the same fictional QA backends, Chromium, production nginx,
Lighthouse 13.5.0 and simulated mobile throttling. No builds, tests or browser
interactions overlapped these measurements. Configuration and exact values are
retained in [performance-lab.json](performance-lab.json).

| Screen | Performance score | LCP | TBT | CLS |
| --- | --- | --- | --- | --- |
| Previous Home, two runs | 79 | 5.10s / 5.10s | 53ms / 44ms | <0.001 |
| Final Home, two runs | 79 | 5.09s / 5.08s | 39ms / 40ms | <0.001 |
| Final help center, one run | 89 | 3.55s | 46ms | 0 |

Home remains in the same performance range; this release does not establish a
speedup. Home and the help center scored 100 for Lighthouse's automated
accessibility checks in these runs. Authenticated admin/customer screens were
checked separately as described above. The help center has no comparable previous
page because its old route redirected to the catalog.

The simulated mobile LCP remains above the 2.5s target. TBT is a laboratory metric,
not field INP. These small local samples do not measure p75 real visits or prove
good Core Web Vitals. The existing request/image-discovery performance work remains
a follow-up, along with real-device/visit evidence under the measurement plan.

## Practical boundaries

The shop still purchases labels, hands parcels to carriers and verifies delivery.
Email acceptance by SMTP does not establish arrival in a customer's inbox; monitor
bounces with the provider. A worker crash after SMTP acceptance can cause a
duplicate, as documented in [purchase journey](../../../backend/product-service/docs/purchase-journey.md).
Older production orders receive no invented dates or unsolicited email backlog.

No production catalog imagery, policies, testimonials or contact identity was
invented. QA fixture images remain clearly fictional. Real support details,
reviewed shipping/return/privacy terms, providers, durable storage and operational
backups are owner setup work. The exact checklist is in
[production setup](../../production-setup.md).

Conversion, retention and reduced support volume remain hypotheses. No analytics
or customer telemetry was added. The existing measurement plan remains applicable;
real-visit performance and those outcomes require separately authorized evidence.
