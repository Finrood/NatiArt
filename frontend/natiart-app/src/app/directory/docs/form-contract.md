# Form contract (CA33)

CEP/CPF/phone format only the input view. Controls contain digits even when an
already formatted value is pasted. Control/Meta clipboard, select-all and undo
shortcuts are preserved. Optional validators accept empty/null values; required
is applied separately. Normalized CEP reaches shipping estimation after typing
or pasting through its rendered input.

Password hints and validators both use DEFAULT_REQUIREMENTS: eight characters
minimum, upper/lowercase and a number, at most 72 UTF-8 bytes. Minimum and byte
limit match the CA6 registration server contract; existing browser character
class requirements are preserved. Login is unchanged for existing accounts.
Hints tolerate null/reset. Shared fields render complexity/length errors and
confirm-password group mismatch with role=alert and a described input. Reactive
form events mark the field for rendering even when its FormGroup identity stays
unchanged. A matching confirmation clears the mismatch and enables Next.

CA34 should retain these validation messages and group subscriptions when adding
its unique control/error IDs and projected-input accessibility. CA29 address
forms use the same normalized CEP and must retain their manual fallback.

Password guidance displays four readable complexity requirements, with neutral
bullets for an empty value and checks only when met. The 72-byte server/client
limit is unchanged; an over-limit value shows a plain-language shortening
message. Login and signup use the shared named Show/Hide control. Projected
field-hint content sits outside the input/toggle container, avoiding a toggle
that stretches across the requirement list. Revealing a password changes only
the native input type; it does not alter the control value or submit the form.

Login renders its error and submission state from signals. A rejected request
must display the localized alert and enable retry without another keystroke or
manual change detection. A new attempt clears the previous alert while retaining
the entered credentials and the existing duplicate-submit guard. Pending login
and stored-session validation subscriptions end when the login view is destroyed.
The rendered rejection/retry regression test uses zoneless change detection to
match application bootstrap, and waits for normal rendering after the HTTP error
without forcing a refresh.
