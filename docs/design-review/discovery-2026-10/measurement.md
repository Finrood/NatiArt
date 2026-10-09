# Evidence and next experiments

## Comparable lab performance

One baseline and one final cold mobile Lighthouse run against the same disposable
Home preview, Chromium, Lighthouse version, default simulated mobile throttling
and configuration. Exact timestamps/configuration are in `performance-lab.json`.
This single pair has no confidence interval and does not establish field p75.

| Metric | Baseline | Final |
| --- | ---: | ---: |
| Performance score | 80 | 79 |
| Accessibility score | 100 | 100 |
| LCP | 5,049ms | 4,973ms |
| CLS | 0.0000304 | 0.0000304 |
| Total blocking time | 40ms | 74ms |
| Speed index | 2,075ms | 2,085ms |

No demonstrated performance gain: LCP still exceeds the 2.5s target. TBT is a
lab diagnostic, not INP. The final LCP candidate is a first featured card image
resolved through product/image APIs to a blob; Lighthouse flags lazy loading and
request discovery. Next profile that dependency chain and visible-card priority
on real devices before assuming that an image attribute alone solves the delay.
No added commercial images/fonts/effects increase the delivered asset set; image
dimensions remain reserved. Do not trade authorized image access or object-URL
cleanup for a lower score.

Field goals are p75 LCP ≤2.5s, INP ≤200ms and CLS ≤0.1, measured over real visits
as described by [Web Vitals](https://web.dev/articles/vitals). Local tests cannot
establish those percentiles.

## Minimal validation plan — no tracking installed

No customer analytics integration was found in the frontend sources/package.
Existing error reporting is not a funnel or retention dataset. Start with
moderated tasks and authorized aggregate order reporting. Any later telemetry
needs owner approval, a documented privacy basis and a retention policy. Never
send identity, email, CPF, address, search text, filenames, artwork bytes/URLs,
order IDs, payment IDs, PIX payloads or payment details to analytics.

| Priority / hypothesis | Problem and intervention | Metric / validation |
| --- | --- | --- |
| 1 / Gift discovery | A tall introduction delays merchandise; compact hero and persistent search reduce that effort. | Moderated gift-finding task success, time to suitable product and wrong turns. EN/PT phone participants compare the matched baseline/final. Success is task completion, not longer sessions. |
| 2 / Purchase confidence | Required artwork and failed submission can surprise shoppers; early guidance, original-image preview and retained options explain the next step. | Personalization task completion and recovery after a staged failure; false expectations about the finished item; required-field error recovery. Run once native upload access is enabled. |
| 3 / Reliable repeat shopping | Completed-payment reload/resume can be ambiguous; status-first confirmation, authorized receipt and fresh second checkout reduce uncertainty. | Checkout completion among started attempts, successful recovery without duplicate order/payment, support confusion. Separate retries from new checkouts; use consented sessions or approved aggregate reporting. |

Shopping completion is distinct from retention. Once the primary task checks
pass, compare returning-shopper and repeat-purchase rates over **60 and 90 days**
among eligible first purchasers, excluding synthetic orders and accounting for
seasonality/product availability. Keep identity joins within an authorized
commerce reporting system; export only aggregate counts. A visual change alone
cannot attribute repeat purchases.

Future features are conditional: test an occasion-led collection only after its
taxonomy/content is approved; test recently viewed pieces only if comparison
research shows a real need and local persistence/privacy is defined; consider
favorites/restock updates only with complete account, inventory, consent and
notification workflows. No inert control, forced subscription or scarcity claim
belongs in the current experience.
