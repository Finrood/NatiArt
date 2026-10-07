interface StoredAttemptIdentity {
  username?: string;
  currentOrder?: {id?: string} | null;
}

export function checkoutAttemptKey(username: string): string {
  return 'natiart-checkout-attempt:' + encodeURIComponent(username.trim().toLowerCase());
}

/** A confirmation for an older payment must never discard a newer checkout. */
export function clearCompletedCheckoutAttempt(storage: Storage, username: string, orderId: string): void {
  for (const key of [checkoutAttemptKey(username), 'natiart-checkout-attempt']) {
    const raw: string | null = storage.getItem(key);
    if (!raw) continue;
    const attempt: StoredAttemptIdentity = JSON.parse(raw) as StoredAttemptIdentity;
    if (attempt.username === username && attempt.currentOrder?.id === orderId) storage.removeItem(key);
  }
}
