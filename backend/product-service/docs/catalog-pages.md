# Catalog pages (CA23)

GET `/products/page`, `/categories/page`, and `/packages/page` return
`{items,total,page,size,hasNext}`. Page is zero based; size is bounded to 1–100.
Sort is label then ID, including ties. Existing array endpoints remain compatible.
Public pages filter inactive records before paging; hiding a category hides its
products. `/admin/{resource}/page` requires ADMIN and includes inactive records.
Product pages accept `categoryId` and a case-insensitive `query` (label contains,
trimmed, maximum 200 characters). ID paging precedes fetching associations so
image joins do not distort page counts.

The `/products` storefront route owns category, query, and page in URL query
parameters. Back/reload restores the same request; obsolete requests are
cancelled. Admin lists show metadata and previous/next controls. Product forms
can load further category/package options without losing previous selections.
Successful writes refresh metadata; an empty final page falls back to the final
remaining page. Failed page loads expose retry and preserve request state.

Deploy the page endpoints before the frontend that consumes them. CA24 public
visibility policies must retain these active-only predicates and ADMIN routes.
When combining CA26, retain the ordered image manifest/editor session handling
alongside pagination. CA27 menus should link to this catalog route; CA28 pending
write/session guards should retain the successful-write page refresh.

Regression evidence: real JPA and HTTP reads with 21 resources and duplicate
labels; authenticated ADMIN/USER boundary checks; rendered later-page editing
and selectors; actual router category clicks, Back navigation, cancellation and
same-page retry. No schema migration is required for this API change.
