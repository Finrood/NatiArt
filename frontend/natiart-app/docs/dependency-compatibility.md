# Frontend dependency compatibility

Angular packages use the stable 22.2.1 release. Keep the framework, compiler,
localization, CDK and build tooling on a compatible release when updating.

## TypeScript

Angular 22.2.1 requires TypeScript >=6.0 <6.1. TypeScript 6.0.3 is the newest
compatible release. TypeScript 7.0.2 causes `npm ci` to fail with `ERESOLVE`.
Dependabot excludes TypeScript >=6.1 until an Angular update supports it.
Check the installed build/compiler peer dependencies and the
[Angular compatibility table](https://angular.dev/reference/versions) before
removing that exclusion.

## Jasmine

The Karma/Zone.js suite uses jasmine-core 6.3.0 and @types/jasmine 6.0.0, the
newest compatible releases available during this update. Jasmine 7 makes the
API properties that Zone.js patches read-only; the suite fails before any
spec executes with `Cannot assign to read only property 'describe'`.
The [Jasmine 7 upgrade guide](https://jasmine.github.io/upgrade-guides/7.0)
confirms that Karma and Zone.js are incompatible with Jasmine 7.
Dependabot excludes Jasmine 7 and its type definitions. Remove those exclusions
only after migrating to a compatible test setup and passing the existing suite.

## Remaining audit finding

The Karma file watcher depends on braces 3.0.3. Its
[stack-exhaustion advisory](https://github.com/advisories/GHSA-vfj7-8cjw-p6xm)
has no patched release. npm audit reports six high-severity entries through
that dependency chain, all in development tooling. npm's proposed fixes
would downgrade Angular and Karma; do not apply them with `--force`.
This finding requires an upstream fix or a separate test-runner migration.
