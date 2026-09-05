import {Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable, throwError} from "rxjs";
import {environment} from "../../../environments/environment";
import {User} from "../models/user.model";
import {UserRegistration} from "../models/user-registration.model";
import {ViaCEPResponse} from "../models/viaCEPResponse.model";
import {LoginResponse} from "../models/loginResponse.model";


@Injectable({
  providedIn: 'root'
})
export class SignupService {
  private readonly apiUrl: string = `${environment.api.directory.url}`;

  constructor(private http: HttpClient) {
  }

  registerUser(userRegistration: UserRegistration): Observable<User> {
    return this.http.post<User>(`${this.apiUrl}/register-user`, userRegistration);
  }

  registerGhostUser(userRegistration: UserRegistration): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/register-ghost-user`, userRegistration);
  }

  getAddressFromZipCode(zipCode: string): Observable<ViaCEPResponse> {
    const digits: string = (zipCode ?? '').replace(/\D/g, '');
    if (!/^\d{8}$/.test(digits)) {
      return throwError(() => new Error('Invalid zip code: expected 8 digits'));
    }
    return this.http.get<ViaCEPResponse>(`${environment.api.viaCep.url}/${digits}/json/`);
  }
}
