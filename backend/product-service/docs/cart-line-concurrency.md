# Cart line mutation

Adding, decreasing, and deleting an existing cart line take a database write lock
on its `(username, product_id)` row before deciding what to do. A second mutation
waits for the first transaction to commit, then observes its quantity or absence.
The last-unit deletion removes the managed `CartItem`, so JPA also removes its
personalization and option rows.

The row lock cannot protect a line that has never been inserted. Two first adds
may both observe no row; the database's `uk_cart_item_user_product` unique
constraint allows only one insert. The losing request fails and may be retried
as a new request, which then locks and increments the winner's row. Keep that
constraint present in PostgreSQL before enabling concurrent cart writes.

The 100-unit line cap is checked under the same row lock. Concurrent adds to a
99-unit line yield one 100-unit line and one rejected add.
