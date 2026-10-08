import {inject, Injectable} from '@angular/core';
import {HttpClient} from "@angular/common/http";
import {catchError, defer, map, Observable, of, tap, throwError, timeout} from "rxjs";
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
  private readonly _addressCache: Map<string, {address: ViaCEPResponse; expiresAt: number}> = new Map();
  private readonly _cacheTtlMs: number = 10 * 60 * 1000;
  private readonly _cacheLimit: number = 50;

  registerUser(userRegistration: UserRegistration): Observable<User> {
    return this._http.post<User>(`${this.apiUrl}${environment.api.directory.endpoints.registerUser}`, userRegistration);
  }

  getAddressFromZipCode(zipCode: string): Observable<ViaCEPResponse> {
    const digits: string = (zipCode ?? '').replace(/\D/g, '');
    if (!/^\d{5}-?\d{3}$/.test((zipCode ?? '').trim())) {
      return throwError(() => new Error('Invalid zip code: expected 8 digits'));
    }
    return defer((): Observable<ViaCEPResponse> => {
      const cached: {address: ViaCEPResponse; expiresAt: number} | undefined = this._addressCache.get(digits);
      if (cached && cached.expiresAt > Date.now()) return of({...cached.address});
      this._addressCache.delete(digits);
      return this._http.get<unknown>(`${environment.api.viaCep.url}/${digits}/json/`).pipe(
        timeout(3000),
        map((data: unknown): ViaCEPResponse => this.normalizeAddress(data, digits, 'viaCep')),
        catchError((): Observable<ViaCEPResponse> =>
          this._http.get<unknown>(`${environment.api.brasilApiCep.url}/${digits}`).pipe(
            timeout(8000),
            map((data: unknown): ViaCEPResponse => this.normalizeAddress(data, digits, 'brasilApi'))
          )),
        tap((address: ViaCEPResponse): void => {
          if (this._addressCache.size >= this._cacheLimit) {
            this._addressCache.delete(this._addressCache.keys().next().value!);
          }
          this._addressCache.set(digits, {address: {...address}, expiresAt: Date.now() + this._cacheTtlMs});
        })
      );
    });
  }

  private normalizeAddress(data: unknown, requestedCep: string, provider: 'viaCep' | 'brasilApi'): ViaCEPResponse {
    if (!data || typeof data !== 'object') throw new Error('Invalid address response');
    const payload: Record<string, unknown> = data as Record<string, unknown>;
    const cep: unknown = payload['cep'];
    const city: unknown = payload[provider === 'viaCep' ? 'localidade' : 'city'];
    const state: unknown = payload[provider === 'viaCep' ? 'uf' : 'state'];
    const street: unknown = payload[provider === 'viaCep' ? 'logradouro' : 'street'];
    const neighborhood: unknown = payload[provider === 'viaCep' ? 'bairro' : 'neighborhood'];
    if (payload['erro'] || typeof cep !== 'string' || !/^\d{5}-?\d{3}$/.test(cep)
      || cep.replace(/\D/g, '') !== requestedCep || typeof city !== 'string' || !city.trim() || city.length > 100
      || typeof state !== 'string' || !/^(AC|AL|AP|AM|BA|CE|DF|ES|GO|MA|MT|MS|MG|PA|PB|PR|PE|PI|RJ|RN|RS|RO|RR|SC|SP|SE|TO)$/.test(state)
      || (street != null && (typeof street !== 'string' || street.length > 255))
      || (neighborhood != null && (typeof neighborhood !== 'string' || neighborhood.length > 100))) {
      throw new Error('Invalid address response');
    }
    return {
      cep: requestedCep, localidade: city.trim(), uf: state,
      logradouro: typeof street === 'string' ? street.trim() : '',
      bairro: typeof neighborhood === 'string' ? neighborhood.trim() : '',
      complemento: '', ibge: '', gia: '', ddd: '', siafi: ''
    };
  }
}
