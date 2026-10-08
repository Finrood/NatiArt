# Address and freight integration audit — 2026-10-08

## Service decision

Keep ViaCEP as the primary address suggestion source and use BrasilAPI CEP v1 as
the fallback. Both suit ordinary customer-entered CEP queries without an API key.
ViaCEP publishes its current database count and warns against bulk database
validation. BrasilAPI is a community/experimental service whose terms prohibit
crawling. Its current implementation tries OpenCEP and then multiple CEP sources.
They share some upstream data: this fallback improves recovery but is not a
contracted availability guarantee. Manual entry remains essential.

v1 returns the address fields this store needs; v2 adds geocoordinates/timezone
work that checkout does not require. Direct OpenCEP is another free alternative,
but adding it separately would overlap BrasilAPI's current first source. No bulk
queries or database downloads were performed. CEP is a suggestion, not proof of
deliverability, house number, apartment, or resident identity.

For freight, Melhor Envio remains a reasonable no-subscription choice for this
single store. Its public FAQ says the platform has no usage fee or minimum volume;
actual shipments cost money. It quotes negotiated carrier prices. There is no
universal cheapest service: price depends on route, actual packed geometry,
insurance, available services and the account's settings.

| Alternative | Official offering checked | Decision |
| --- | --- | --- |
| Frenet | R$0 Iniciante plan includes carrier quotes; API token is available from the account | Credible alternative; compare real routes before paying migration cost |
| SuperFrete | Site-own API integration; no subscription/minimum charge advertised, shipment charges apply | Credible alternative; compare coverage, account approval and dispatch workflow |
| Correios direct | Price/time APIs require a qualifying billing contract and enabled API service codes | Useful when dispatch uses that contract; not an anonymous free replacement |

The webshop currently defaults to the cheapest usable **Correios** option.
Additional approved carriers can be enabled through `MELHORENVIO_ALLOWED_COMPANIES`
and the Melhor Envio account's service settings. Do not enable a carrier before
checking its handling rules, reachable drop-off point and fragile-art eligibility.
The storefront does not yet offer a cheapest-versus-fastest service selector.

## Corrections shipped with this audit

- Address lookup has bounded timeouts, a backup, a small successful-result cache,
  response/CEP validation and cancellation that also recovers when retyping the
  same CEP. Manual corrections and number/apartment survive.
- The dispatch origin default matches the owner's confirmed CEP. Production
  requires an explicit origin instead of inheriting a development default.
- Mandatory User-Agent includes an application name and technical contact.
- Prices/times honor `custom_*`; the hardcoded R$5 compensation is removed.
- Checkout sends insured merchandise value for each parcel, including server-priced
  personalization. Units remain numeric cm/kg/BRL with one packed parcel per unit.
- Invalid provider options fail closed. Provider credential errors cannot become
  shopper authorization failures. Retries and timeouts are bounded.
- Package changes invalidate old quotes, including changes that leave the product
  version unchanged. Product/package data is fetched together for order validation.
- All shipping endpoints share per-client and database-backed provider budgets.
  The provider documents 250 calls/minute per authenticated account; our default
  60 admitted requests/minute leaves room for retries/window boundaries.
- Cart estimation now uses a non-binding server-calculated basket, including
  quantities and insured values, instead of a fixed sample parcel. CEP/basket
  changes cancel old requests and estimates; failures have a same-CEP retry.
- Checkout shows carrier business days **after dispatch**, separately from preparation.

## Production setup and unresolved physical checks

Set `MELHORENVIO_API_URL` to the official production calculation endpoint,
`MELHORENVIO_FROM_POSTAL_CODE` to the dispatch location, `MELHORENVIO_API_TOKEN`
to a valid production token, and `MELHORENVIO_USER_AGENT` to
`NatiArt (your actual technical contact email)`. Keep the token in the secret store.
The development contact is a deliberately non-deliverable placeholder and must
not be used in production. Carrier allowlist defaults to Correios; budget is
`NATIART_SHIPPING_MAX_REQUESTS_PER_MINUTE` (60 by default). Raising it requires
reviewing retries, other integrations on the account and provider limits together.

The implementation uses a configured static token. **Automatic OAuth renewal is
not implemented.** Official OAuth access tokens last 30 days and refresh tokens
45 days. Verify the configured credential's type/expiry in the provider account
and arrange renewal/alerts, or implement the OAuth flow before unattended launch.
No real credential was inspected, rotated or published during this audit.

The owner has not chosen a shipping-label workflow. API label automation is
unnecessary: labels can be bought manually through Melhor Envio. However, paying
at the post-office counter uses a different price table. Do not collect discounted
Melhor Envio freight and assume a fixed surcharge covers counter pricing. Choose
the dispatch channel before accepting real orders.

Reweigh all active products with boxes/protection, check outer dimensions and
insured values, and ensure fulfillment uses the quoted one-parcel-per-unit plan.
Combining items into one physical box requires a reviewed packer and requoting;
the current conservative rule can make multi-item freight more expensive. Older
net weights cannot be converted without measurements. Also establish preparation
times, invoices/content declarations, tracking and a returns/damage workflow.

Before launch, compare production quotes against the provider dashboard for
local, regional, distant and remote CEPs, quantities one/multiple, and personalized
pieces. Match origin, parcels, declared value and service exactly. Check 429,
invalid/expired token and provider outage recovery without allowing payment with
missing freight. The local automated provider fixtures validate request/response
contracts and order integrity; they do **not** establish real postage prices,
insurance coverage or capacity for 1,000 concurrent shoppers.

Native screenshots and automated-check results are recorded in
[the verification evidence](../../../docs/design-review/shipping-2026-10/README.md).

## Sources checked

- [ViaCEP contract and usage warning](https://viacep.com.br/)
- [BrasilAPI service terms](https://brasilapi.com.br/),
  [CEP provider implementation](https://github.com/BrasilAPI/BrasilAPI/blob/main/services/cep/cep.js),
  [CEP v1](https://github.com/BrasilAPI/BrasilAPI/blob/main/pages/api/cep/v1/%5Bcep%5D.js)
- [Melhor Envio calculation contract](https://docs.melhorenvio.com.br/reference/calculo-de-fretes-por-produtos),
  [integration guidance](https://docs.melhorenvio.com.br/docs/cotacao-de-fretes),
  [limits and token expiry](https://docs.melhorenvio.com.br/reference/faq)
- [Melhor Envio free-use FAQ](https://centraldeajuda.melhorenvio.com.br/hc/pt-br/articles/31220745560596-%C3%89-novo-por-aqui-Acesse-respostas-r%C3%A1pidas-para-suas-principais-d%C3%BAvidas)
- [Frenet plans](https://ajuda.frenet.com.br/knowledge-base/2090938-qual-o-valor-e-diferenca-dos-planos/),
  [API access](https://ajuda.frenet.com.br/knowledge-base/api-frenet/)
- [SuperFrete integrations and fees](https://superfrete.com/integracao-frete)
- [Correios Price API](https://www.correios.com.br/atendimento/developers/manuais/manual-api-preco-1),
  [Time API](https://www.correios.com.br/atendimento/developers/manuais/manual-api-prazo)
