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

  private readonly authStateListeners = new Set<() => void>();

  /** Registers a callback invoked whenever tokens are cleared. Returns an unsubscribe function. */
  onTokensCleared(listener: () => void): () => void {
    this.authStateListeners.add(listener);
    return (): void => {
      this.authStateListeners.delete(listener);
    };
  }

  clearTokens(): void {
    this.accessToken = null;
    this.refreshToken = null;
    for (const listener of Array.from(this.authStateListeners)) {
      try {
        listener();
      } catch {
        // One failing listener must not block the remaining session teardown.
      }
    }
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
