# Public catalog visibility

Hiding a category hides the category and all of its products from public
category and product listing, featured/new listing, and detail routes. A
product's own visibility flag must also be active. Public product queries
filter both flags before pagination and again when loading images for the
selected page. Stock reservation rejects products hidden by either flag.

Administrators can still read hidden categories and products through the
same routes so they can restore them. Internal order reads keep access to
historical product records; hiding a catalog item does not erase an order.
