
The integrated CA23/CA24/CA26 branch retains public active-only legacy lists,
filtered bounded pages and administrator discovery, alongside ordered image
edit manifests and stale edit-session guards. Public page association reloads
also require active products/categories. Actual JPA/controller tests cover
21-row traversal and hidden product/category exclusions; image manifest HTTP
and browser tests run with the current category-aware controller and paged UI.
