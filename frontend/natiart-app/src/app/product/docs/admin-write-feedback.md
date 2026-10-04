# Admin writes (CA28)

Category/package forms prevent a second submission while a write is pending.
Signal-backed pending/modal state and shared alerts publish HTTP completions,
including failures, into the rendered UI. A failed write keeps input and enables
retry. Form generations prevent old replies from closing a newly opened edit;
the global pending guard remains until the earlier request finishes. Component
destruction cancels its write subscription. Delete confirmation names the row;
reference conflicts instruct reassignment, preserving historical order data.

When combining CA23 pagination, retain metadata refresh on successful writes
alongside these guards and generation checks. CA23 page-loader retry replaces
legacy list-loading alerts. No server write accepted before client cancellation
is rolled back merely by leaving the screen.

Rendered HTTP regressions cover delayed double submit, visible error/retry and
input retention, close/reopen with a pending response, and named delete cancel
for both resources. These checks caught an alert component that held new errors
in memory while its rendered view stayed unchanged.
