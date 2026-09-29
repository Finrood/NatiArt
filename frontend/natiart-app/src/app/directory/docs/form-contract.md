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
