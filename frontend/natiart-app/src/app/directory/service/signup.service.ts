import {Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs";
import {environment} from "../../../environments/environment";
import {User} from "../models/user.model";
import {UserRegistration} from "../models/user-registration.model";
import {ViaCEPResponse} from "../models/viaCEPResponse.model";


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

  registerGhostUser(userRegistration: UserRegistration): Observable<User> {
    return this.http.post<User>(`${this.apiUrl}/register-ghost-user`, userRegistration);
  }

  getAddressFromZipCode(zipCode: string): Observable<ViaCEPResponse> {
    return this.http.get<ViaCEPResponse>(`https://viacep.com.br/ws/${zipCode}/json/`);
  }
}
