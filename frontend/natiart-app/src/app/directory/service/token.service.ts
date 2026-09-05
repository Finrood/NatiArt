import {Injectable} from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class TokenService {

  constructor() { }

  get accessToken(): string | null {
    return TokenService.read('accessToken');
  }

  set accessToken(value: string | null) {
    TokenService.write('accessToken', value);
  }

  get refreshToken(): string | null {
    return TokenService.read('refreshToken');
  }

  set refreshToken(value: string | null) {
    TokenService.write('refreshToken', value);
  }

  clearTokens(): void {
    this.accessToken = null;
    this.refreshToken = null;
  }

  private static read(key: string): string | null {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  }

  private static write(key: string, value: string | null): void {
    try {
      if (value) localStorage.setItem(key, value);
      else localStorage.removeItem(key);
    } catch {
      // Storage unavailable (private mode, quota, non-browser context):
      // tokens stay memory-only for this session instead of crashing the flow.
    }
  }
}
