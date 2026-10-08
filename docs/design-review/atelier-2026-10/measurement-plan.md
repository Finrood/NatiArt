# Completion and return-purchase hypotheses

Visual consistency is an intervention, not evidence of higher conversion or
retention. No tracking service or new customer analytics was installed.
Existing sanitized error reporting remains the only shipped reporting path.

| Priority | User problem / intervention | Primary measure | Validation |
| --- | --- | --- | --- |
| 1 | Dense discovery makes comparing gifts harder. Quiet shared cards and earlier merchandise. | Product-detail reach from Collections, then add-to-cart and purchase completion per eligible visit. | Moderated gift-finding/comparison tasks in Portuguese first; then a controlled experiment with device/locale segments and unchanged merchandise. Check completion time/errors, not longer sessions. |
| 2 | A hidden personalization decision or lost choice interrupts buying. Choose options, retained selections and explicit retry. | Personalization completion; retry-to-completion after a failed request; abandonment at the dialog. | Simulated failure tasks with gold and artwork, keyboard/mobile participants; subsequently compare cohorts/experiment without recording files or their names. |
| 3 | Form effort and ambiguous charges discourage checkout. Prefill/autofill, readable steps, provisional estimate and final quote. | Checkout completion from checkout start; form-error recovery; quote-to-order completion. | Observe CPF/CEP/house-number tasks and error recovery; controlled field evaluation, retaining authentication and validation requirements. |
| 4 | Customers cannot easily inspect a purchase or buy again. Owned-order success link, readable details and product links. | Returning shopper rate and second confirmed purchase within 60/90 days. | Owner-approved first-party aggregate cohorts with adequate follow-up, excluding test/cancelled orders; choose window using actual handmade-goods purchase frequency. Separate this from single-session checkout completion. |

A minimal future event schema would contain only event name, screen, locale,
coarse viewport bucket, result/error category and duration bucket. Useful events:
collection viewed, product opened, personalization opened/completed/retried,
checkout step completed, field-error recovered, confirmed-purchase outcome and
order-view-to-product return. Avoid government IDs, email, address, phone,
artwork/file names, product-upload paths, order/payment IDs, credentials, QR
payloads and free text. Do not put these values into analytics or URL dimensions.

Any future repeat-purchase analysis should compute aggregate order cohorts
inside the authorized backend/reporting boundary. Do not export account identity
into client analytics. Business approval, appropriate consent/retention decisions,
traffic sizing and an agreed experiment stopping rule precede rollout.

For performance, future authorized real-user measurement should report p75 LCP,
INP and CLS by mobile/desktop and locale. Targets are LCP ≤2.5s, INP ≤200ms and
CLS ≤0.1. Local Lighthouse is lab evidence; TBT is not field INP. Next performance
work should trace hero discovery and below-fold product-image/API requests. The
last Home audit identifies the hero as LCP, discovered only after the lazy Home
bundle; measure scoped discovery/defer changes over repeated cold runs before
adding preload directives that might compete with useful resources on other routes.

The October 8 refinement implements Home-only hero discovery and deferred
new-product image loading. Its [repeated lab evidence](refinement/README.md)
supersedes the above discovery finding. Continue measuring field p75 before
claiming a performance or conversion outcome. Research the stronger portrait
composition versus immediate product-grid exposure on phones, and verify that
step-heading focus improves payment-step comprehension and error recovery.

Favorites, restock notifications and collection subscriptions remain future
options only after research identifies a need and account/data/email delivery
can be implemented completely. No inert engagement controls were added.
