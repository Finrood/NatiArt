import {inject, Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable, throwError} from "rxjs";
import {environment} from "../../../environments/environment";
import {User} from "../models/user.model";
import {UserRegistration} from "../models/user-registration.model";
import {ViaCEPResponse} from "../models/viaCEPResponse.model";


@Injectable({
  providedIn: 'root'
})
export class SignupService {
  private readonly apiUrl: string = `${environment.api.directory.url}`;

  private readonly _http: HttpClient = inject(HttpClient);

  registerUser(userRegistration: UserRegistration): Observable<User> {
    return this._http.post<User>(`${this.apiUrl}${environment.api.directory.endpoints.registerUser}`, userRegistration);
  }

  getAddressFromZipCode(zipCode: string): Observable<ViaCEPResponse> {
    const digits: string = (zipCode ?? '').replace(/\D/g, '');
    if (!/^\d{8}$/.test(digits)) {
      return throwError(() => new Error('Invalid zip code: expected 8 digits'));
    }
    return this._http.get<ViaCEPResponse>(`${environment.api.viaCep.url}/${digits}/json/`);
  }
}
