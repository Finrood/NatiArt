# Order text boundary

Required contact and address fields, including house number, are stripped and
bounded to the persisted 255-character columns before fingerprinting, provider
calls or stock reservation. Optional contact/address text is stripped and
bounded too. The validated DTO supplies the persisted snapshot. CEP is stored
in its eight-digit form. An address without a house number uses N/A; historical
null house numbers remain readable.

The API returns a bounded field/message 400 without echoing submitted text.
Committed HTTP/JPA tests cover padded maximum-length contact/address values,
256-character rejection, null/blank house number, N/A, and normalized replay
without a second stock reservation. CA29 supplies the manual CEP fallback and
forward/back editing behavior retained in this integration.
