import {checkoutAttemptKey, clearCompletedCheckoutAttempt} from './checkout-attempt-storage';

describe('Completed checkout attempt storage', (): void => {
  const username: string = 'buyer@example.test';
  const key: string = checkoutAttemptKey(username);
  afterEach((): void => {localStorage.removeItem(key); localStorage.removeItem('natiart-checkout-attempt');});
  it('clears only the confirmed account and order, including legacy storage', (): void => {
    const attempt: string = JSON.stringify({username, currentOrder: {id: 'paid'}});
    localStorage.setItem(key, attempt); localStorage.setItem('natiart-checkout-attempt', attempt);
    clearCompletedCheckoutAttempt(localStorage, username, 'paid');
    expect(localStorage.getItem(key)).toBeNull();
    expect(localStorage.getItem('natiart-checkout-attempt')).toBeNull();
  });
  it('preserves a newer order and another account’s legacy attempt', (): void => {
    localStorage.setItem(key, JSON.stringify({username, currentOrder: {id: 'new'}}));
    localStorage.setItem('natiart-checkout-attempt', JSON.stringify({username: 'other', currentOrder: {id: 'paid'}}));
    clearCompletedCheckoutAttempt(localStorage, username, 'paid');
    expect(localStorage.getItem(key)).toContain('new');
    expect(localStorage.getItem('natiart-checkout-attempt')).toContain('other');
  });
  it('keeps recovery data when storage removal fails', (): void => {
    localStorage.setItem(key, JSON.stringify({username, currentOrder: {id: 'paid'}}));
    const remove: jasmine.Spy = spyOn(Storage.prototype, 'removeItem').and.throwError('unavailable');
    expect((): void => clearCompletedCheckoutAttempt(localStorage, username, 'paid')).toThrowError('unavailable');
    expect(localStorage.getItem(key)).toContain('paid');
    remove.and.callThrough();
  });
});
