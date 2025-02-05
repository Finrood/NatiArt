import {Injectable} from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class TokenService {

  constructor() { }

  get accessToken(): string | null {
    return localStorage.getItem('accessToken');
  }

  set accessToken(value: string | null) {
    if (value) localStorage.setItem('accessToken', value);
    else localStorage.removeItem('accessToken');
  }

  get refreshToken(): string | null {
    return localStorage.getItem('refreshToken');
  }

  set refreshToken(value: string | null) {
    if (value) localStorage.setItem('refreshToken', value);
    else localStorage.removeItem('refreshToken');
  }

  clearTokens() {
    this.accessToken = null;
    this.refreshToken = null;
  }
}
