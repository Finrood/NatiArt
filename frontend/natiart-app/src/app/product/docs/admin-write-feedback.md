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

Product editors retain an option for each original category/package outside the loaded
page, using the product response's reference label. Loading that reference's page
replaces the retained option without duplication; loaded inactive references are
identified explicitly. Closing/reopening resets the selected reference snapshot.

Product writes now use the same pending, generation and destruction contract.
A late save may refresh the list but cannot close a different editor opened
afterward. Visibility/delete requests share a per-product pending guard,
disabling conflicting row actions until completion. Product deletion names the
row and requires confirmation. Every editor starts from explicit defaults,
including personalization flags, before patching a selected product. Cancelling
a subscription does not undo a server write already accepted.
