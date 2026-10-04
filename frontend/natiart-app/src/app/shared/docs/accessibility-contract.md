# Shared accessibility contract

Projected form inputs, selects and textareas receive unique IDs, required and invalid state, and the current error description from `app-natiart-form-field`. Existing external hint descriptions are retained. Explicit IDs remain available for address forms. Form events refresh errors without replacing the form object. Password buttons expose their current Show/Hide name and pressed state.

Use `app-accessible-dialog` for modal content. Native `showModal()` provides focus containment and inert background content; Escape and backdrop dismissal emit `dismiss` to the owner. Closing restores the connected opener and restores body scrolling after the last dialog closes. Confirmation starts at Cancel. Every owner must handle dismissal and destroy the component when closed.

The carousel has visible keyboard controls and explicit pause/resume. Focus and pointer presence pause automatic rotation. Reduced motion disables rotation and slide transitions; manual selection remains available. Explore Collections uses the public `/products` route supplied by CA23/CA27.

## Verification

256 ChromeHeadless specs cover native modal/background focus, nested scroll restoration, rendered category/package/product/personalization/cart journeys, projected labels and errors, password state, and pause/reduced-motion carousel behavior.

A separate local browser probe used the real components and disposable HTTP fixtures. Keyboard checks confirmed signup Email → Password tab order; Enter opened all three admin editors; Escape restored each Add button; reverse tab remained within the category modal; personalization Escape restored its opener; cart removal started on Cancel and Escape preserved the line and restored Remove. Clicking Billing Zip Code focused `billingZipCode`. End selected carousel slide 4 and Enter changed Pause to Resume. This is browser keyboard and accessibility-tree evidence, not a claim of a dedicated screen-reader audit.

## Integration

Retain CA33 group/password errors and normalized input behavior, CA37 global projected-input styling/local fonts, CA29 address lookup behavior, CA23 pagination and CA28 write guards when resolving overlapping component files. Shared control IDs/errors should be owned by this wrapper; remove obsolete caller error IDs rather than retain broken descriptions. Keep CA26 product image ordering/upload cancellation and CA13 personalization ownership behavior inside the dialog wrapper. Deploy the public catalog route before exposing the carousel link.
