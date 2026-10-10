import {Injectable} from '@angular/core';

@Injectable({providedIn: 'root'})
export class StoreContactService {
  readonly email: string | null;
  constructor() {
    const value: unknown = (globalThis as typeof globalThis & {__NATIART_CONFIG__?: {supportEmail?: unknown}}).__NATIART_CONFIG__?.supportEmail;
    this.email = typeof value === 'string' && value.length <= 254 && /^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$/.test(value) ? value : null;
  }
}
