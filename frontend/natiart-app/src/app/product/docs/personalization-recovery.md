# Personalization submission and recovery

`AddToCartButtonComponent` owns cart acceptance. Products with gold/custom-image
options expose Choose options. The component passes `closeOnSubmit=false` to
the existing personalization modal: submitting emits the current selection but
does not clear or close it until a fresh product read and cart acceptance succeed.
Other modal owners keep the default close-on-submit behavior.

The freshness request disables repeated submission. A failed read leaves gold,
the selected File and the dialog in place, with a localized inline alert and a
retry through the same submit action. Dismissal unsubscribes pending work and
resets options; destruction also cancels subscriptions. Successful completion
closes through the native dialog so focus returns to its opener.

The success status and item-added event require an actual accepted cart-quantity
increase. A stock cap that accepts no extra quantity must show an error instead
of a false Added message. The server still validates price, stock, shipping and
personalization during checkout. Client file guidance rejects empty/non-image
files and files over 5MB; backend validation and authorized artwork access remain
the authority. Do not log File names, bytes or artwork URLs.

Tests cover failed reads retaining the same File/gold, fresh-product values,
duplicate submission, dismissal cancellation and full-stock feedback. The local
browser verified gold selection retained across a real 503 and successful retry.
Live file-chooser/upload verification was blocked by the Chrome extension's
file-URL permission; it is not claimed passed.

Custom-image products explain the required artwork and 5 MB limit before Choose
options. The dialog initially uses neutral required-field guidance; invalid
selection introduces the associated error and `aria-invalid`. A valid selected
File is previewed through the existing ImageCollection, with a filename and
explicit original-image wording. It is not a finished-product mockup.

Preview ownership follows the File signal: replacement, dismissal and component
destruction release object URLs. A browser that cannot decode the preview shows
a neutral fallback and retains the original valid File for server validation.
Submission failure retains that same File and the gold-border choice. Regression
tests exercise replacement, decoding failure, failed acceptance and cleanup.
