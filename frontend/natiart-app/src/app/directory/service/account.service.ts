import {inject, Injectable} from '@angular/core';
import {HttpClient, HttpErrorResponse} from '@angular/common/http';
import {Observable} from 'rxjs';
import {environment} from '../../../environments/environment';

@Injectable({providedIn: 'root'})
export class AccountService {
  private readonly _http: HttpClient = inject(HttpClient);
  changePassword(currentPassword: string, password: string, passwordConfirmation: string): Observable<void> {
    return this._http.post<void>(`${environment.api.directory.url}${environment.api.directory.endpoints.user}${environment.api.directory.endpoints.current}/change-password`,
      {currentPassword, password, passwordConfirmation});
  }
}

export function accountErrorMessage(error: HttpErrorResponse): string {
  switch (error.error?.code) {
    case 'CURRENT_PASSWORD_INCORRECT': return $localize`:@@accountCurrentPasswordError:Your current password is incorrect. Please try again.`;
    case 'TRY_LATER': return $localize`:@@accountTryLater:Too many attempts. Please wait a minute before trying again.`;
    case 'PROFILE_CONFLICT': return $localize`:@@accountProfileConflict:Your details changed in another session. Reload your details before saving again.`;
    case 'ACCOUNT_SETUP_IN_PROGRESS': return $localize`:@@accountSetupBusy:Your account setup is finishing. Please try saving again shortly.`;
    case 'PROVIDER_UNAVAILABLE': return $localize`:@@accountProviderUnavailable:We could not confirm your details with our payment provider. Your edits are still here; please try again.`;
    default: return $localize`:@@accountSaveError:We could not save your changes. Please try again.`;
  }
}
