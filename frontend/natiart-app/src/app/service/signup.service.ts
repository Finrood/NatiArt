import {Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {Observable} from "rxjs";
import {environment} from "../../environments/environment";

export interface SignupRequest {
  username: string;
  password: string;
  profile: {
    firstname: string;
    lastname: string;
    phone?: string;
    country?: string;
    state?: string;
    city?: string;
    zipCode?: string;
    street?: string;
    complement?: string;
  };
}

export interface ViaCEPResponse {
  cep: string;
  logradouro: string;
  complemento: string;
  bairro: string;
  localidade: string;
  uf: string;
  ibge: string;
  gia: string;
  ddd: string;
  siafi: string;
}

@Injectable({
  providedIn: 'root'
})
export class SignupService {
  private readonly apiUrl: string = `${environment.directoryApiUrl}`;

  constructor(private http: HttpClient) {
  }

  registerUser(signupRequest: SignupRequest): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/register-user`, signupRequest);
  }

  registerGhostUser(signupRequest: SignupRequest): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/register-ghost-user`, signupRequest);
  }

  getAddressFromZipCode(zipCode: string): Observable<ViaCEPResponse> {
    return this.http.get<ViaCEPResponse>(`https://viacep.com.br/ws/${zipCode}/json/`);
  }
}
