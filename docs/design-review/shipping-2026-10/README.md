# Address and shipping verification — 2026-10-08

This evidence uses an isolated local preview on port 4300 with the final production
frontend artifacts and rebuilt backend jars. The existing port-4200 owner preview
and its 27 fixture files were preserved. The account, identity data, parcels and
shipping prices shown here are synthetic. No real order, payment, shipment,
credential rotation or production deployment was performed.

## Native browser results

- Portuguese profile CEP changed from 01001-000 to 22041-001: suggestions became
  RJ / Rio de Janeiro / Copacabana / Avenida Atlântica, while house number 456B
  and apartment Apartment 7 remained. The draft was not saved.
- A cart of four vases sent four numeric parcels, each 20 × 15 × 10.5 cm and
  0.8 kg, insured at R$22.99. Origin was 88058-380 and destination 01001-000.
  The mandatory User-Agent was present; no credential is included in this evidence.
- Synthetic provider base price/time were R$18.90 / 7 days and adjusted values
  R$20.40 / 8 days. Both cart and checkout showed the adjusted values, without
  the former R$5 addition. Checkout total was R$112.36 (R$91.96 + R$20.40).
- A simulated carrier outage showed an inline error and retry button. Recovery
  succeeded with the same CEP using that button, without another field edit.
- Checkout retained the stored number/apartment and displayed business days after
  dispatch separately from preparation. Verification stopped before confirming
  the order or creating a payment.
- English and Portuguese screens were reviewed at desktop and 390-pixel phone
  widths. Mobile cart and checkout had no horizontal document overflow.

## Automated validation

- Product-service `check` and `bootJar`: 528 tests, zero failures/errors; formatting
  and the five required boundary suites passed.
- Angular ChromeHeadless: 416 passing tests.
- Both production locales built; production artifact verification and its five
  regression tests passed. All 539 active translation IDs have Portuguese entries.
- Regression coverage includes address timeout/fallback/cancellation/cache,
  third-party JWT exclusion, real basket requests and retry, numeric parcel
  insurance, provider price/time/error contracts, rate budgets across instances,
  quote invalidation on package changes, and real-JPA quote-to-order integrity.

## Captures

- [Address suggestions preserve number/apartment](address-suggestion.jpg)
- [Carrier outage and inline retry](cart-provider-outage.jpg)
- [Recovered Portuguese desktop cart](cart-recovered-desktop.jpg)
- [English phone estimate](cart-estimate-mobile-en.jpg)
- [Portuguese checkout quote](checkout-quote-desktop.jpg)
- [Portuguese phone quote](checkout-quote-mobile.jpg)

These checks validate software contracts and presentation. Real packed weights,
insurance eligibility, account token renewal, purchased-shipment pricing and
capacity for 1,000 concurrent customers remain operational verification tasks.
See [the provider audit](../../../backend/product-service/docs/address-shipping-audit.md).
