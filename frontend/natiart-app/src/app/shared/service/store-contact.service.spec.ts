import {StoreContactService} from './store-contact.service';

describe('Public shop contact', (): void => {
  const runtime = globalThis as typeof globalThis & {__NATIART_CONFIG__?: {supportEmail?: unknown}};
  let previous: typeof runtime.__NATIART_CONFIG__;
  beforeEach((): void => { previous = runtime.__NATIART_CONFIG__; });
  afterEach((): void => { runtime.__NATIART_CONFIG__ = previous; });
  it('accepts a configured mailbox but rejects links, control characters and invalid values', (): void => {
    runtime.__NATIART_CONFIG__ = {supportEmail: 'atelier@example.test'}; expect(new StoreContactService().email).toBe('atelier@example.test');
    for (const value of ['javascript:alert(1)', 'a@example.test?subject=foo', 'a@example.test\r\nBcc:other@example.test', null, 123]) {
      runtime.__NATIART_CONFIG__ = {supportEmail: value}; expect(new StoreContactService().email).toBeNull();
    }
  });
});
