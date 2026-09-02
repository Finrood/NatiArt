# Frontend Guide — natiart-app

Single Angular 20 application (no monorepo), Tailwind CSS 4, Adyen payments,
Karma/Jasmine tests. Generic rules in `agents/*.md` supersede nothing here — this
file is the frontend source of truth.

## Structure

```
src/app/
├── directory/     # auth, user, account screens & services
├── product/       # catalog, cart, orders, payment screens & services
├── shared/        # shared components, pipes, models
├── app.routes.ts  # routing
└── app.config.ts  # providers
```

Screens are feature folders (component + html + scss + spec). New cross-screen
building blocks go in `shared/`. Keep `app.component` a thin shell.

## Angular Idioms

- **Standalone components only** (default — no `standalone:` flag); declare
  dependencies in the `imports: []` array.
- **Control flow**: `@if` / `@for`. Never `*ngIf` / `*ngFor` (already 100% migrated).
- **DI**: `private readonly _x = inject(X)` for new code; convert constructor
  injections when touching (in progress — 7 files done).
- **Signals**: adopt `signal()`/`computed()` for new component state; prefix with
  `$` (e.g. `$user`). RxJS `BehaviorSubject`/streams remain acceptable for
  streaming flows (chat, polling); do not rewrite working RxJS code unprompted.
- **Explicit types everywhere** — fields, parameters, return types, generics.
  No reliance on TypeScript inference beyond trivial primitives.
- **Subscriptions**: unsubscribe properly — `takeUntilDestroyed()` (in injection
  context) or `takeUntil(this._destroyed$)` + `OnDestroy` for older code.
- No lodash in this project — use native array methods.

## Payments & Security

- Adyen integration (`@adyen/adyen-web`) and auth-token handling are
  security-sensitive: never log tokens, never bypass API-provided validation.
- Environments in `src/environments/` — API endpoints per environment; never
  hard-code URLs in components.

## Testing

- Runner: **Karma + Jasmine** (`ng test`, ChromeHeadless in CI —
  `.github/workflows/frontend_workflow.yml`).
- ~55 specs. Policy: **test complex logic** (services, pipes, state handling);
  obvious markup needs no spec. Boilerplate "should create" specs must keep passing
  (they run in CI).
- When adding a component with real logic, add a spec next to it.

## Commands

```bash
npm start                      # dev server
npm run build                  # production build
ng test --watch=false --browsers=ChromeHeadless   # CI-style tests
```
