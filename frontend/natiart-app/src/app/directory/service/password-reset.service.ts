import {Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';

import {environment} from '../../../environments/environment';

export interface PasswordResetResponse {
  message: string;
}

@Injectable({providedIn: 'root'})
export class PasswordResetService {
  private readonly apiUrl = environment.api.directory.url;

  constructor(private readonly http: HttpClient) {}

  requestReset(username: string): Observable<PasswordResetResponse> {
    return this.http.post<PasswordResetResponse>(
      `${this.apiUrl}${environment.api.directory.endpoints.passwordResetRequest}`,
      {username}
    );
  }

  resetPassword(token: string, password: string, passwordConfirmation: string): Observable<void> {
    return this.http.post<void>(
      `${this.apiUrl}${environment.api.directory.endpoints.passwordReset}`,
      {token, password, passwordConfirmation}
    );
  }
}
