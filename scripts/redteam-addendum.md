# RED-TEAM CYCLE (overrides normal procedure steps 2-6)

This is an adversarial cycle: build nothing, break things on paper. Local
read-only probing only — never exfiltrate data, never attack anything outside
this repo, never run destructive commands.

1. Threat-model one sensitive flow (auth, payment, upload, checkout). Write
   down assets, trust boundaries, and attacker capabilities first.
2. Attempt to defeat it: auth bypass, IDOR, price/quantity tampering, path
   traversal, token replay, race conditions. Run the existing test suite for
   the flow and try to construct a currently-passing exploit-shaped input the
   tests do not cover.
3. Output is a `docs/` PR appending new `OPEN` items (with reproduction steps
   and severity) to `docs/audit-findings.md` — never merge exploit code, never
   merge failing tests. If a hole is trivially fixable inside the timebox, fix
   it normally instead (with regression tests) and note the kill in the PR body.
4. If no hole is found, document the threat model compactly in the PR body
   anyway: a recorded negative result is the asset. Merge only on green CI.
