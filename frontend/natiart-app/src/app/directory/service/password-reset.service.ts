import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';

import {environment} from '../../../environments/environment';

export interface PasswordResetResponse {
  message: string;
}

@Injectable({providedIn: 'root'})
export class PasswordResetService {
  private readonly apiUrl: string = environment.api.directory.url;

  private readonly _http: HttpClient = inject(HttpClient);

  requestReset(username: string): Observable<PasswordResetResponse> {
    return this._http.post<PasswordResetResponse>(
      `${this.apiUrl}${environment.api.directory.endpoints.passwordResetRequest}`,
      {username}
    );
  }

  resetPassword(token: string, password: string, passwordConfirmation: string): Observable<void> {
    return this._http.post<void>(
      `${this.apiUrl}${environment.api.directory.endpoints.passwordReset}`,
      {token, password, passwordConfirmation}
    );
  }
}
