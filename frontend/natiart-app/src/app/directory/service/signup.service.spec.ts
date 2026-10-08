import { fakeAsync, tick, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { environment } from '../../../environments/environment';
import { SignupService } from './signup.service';

describe('SignupService', () => {
  let service: SignupService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideHttpClient(), provideHttpClientTesting()]});
    service = TestBed.inject(SignupService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('builds the lookup URL from the environment', () => {
    const original: string = environment.api.viaCep.url;
    environment.api.viaCep.url = 'https://viacep.test/ws';
    try {
      service.getAddressFromZipCode('01001000').subscribe();
      httpTesting.expectOne('https://viacep.test/ws/01001000/json/').flush(primary());
    } finally {
      environment.api.viaCep.url = original;
    }
  });

  it('normalizes formatted zip codes to digits', () => {
    service.getAddressFromZipCode('01001-000').subscribe();
    httpTesting.expectOne(`${environment.api.viaCep.url}/01001000/json/`).flush(primary());
  });

  it('rejects invalid zip codes before any HTTP call', () => {
    const seen: string[] = [];
    service.getAddressFromZipCode('abc').subscribe({error: (e: Error) => seen.push(e.message)});
    service.getAddressFromZipCode('123').subscribe({error: (e: Error) => seen.push(e.message)});
    service.getAddressFromZipCode('abc01001000').subscribe({error: (e: Error) => seen.push(e.message)});

    expect(seen).toEqual(Array(3).fill('Invalid zip code: expected 8 digits'));
    httpTesting.expectNone(() => true);
  });
  function primary(): Record<string, unknown> {
    return {cep: '01001-000', logradouro: 'Praça da Sé', bairro: 'Sé', localidade: 'São Paulo', uf: 'SP'};
  }

  function backup(): Record<string, unknown> {
    return {cep: '01001000', street: 'Praça da Sé', neighborhood: 'Sé', city: 'São Paulo', state: 'SP'};
  }

  for (const payload of [{erro: true}, {erro: 'true'}, {...primary(), cep: '22041001'}, {...primary(), uf: 'ZZ'}, null]) {
    it(`falls back on a missing, mismatched or invalid primary response: ${JSON.stringify(payload)}`, () => {
      let street: string = '';
      service.getAddressFromZipCode('01001000').subscribe(address => street = address.logradouro);
      httpTesting.expectOne(`${environment.api.viaCep.url}/01001000/json/`).flush(payload);
      httpTesting.expectOne(`${environment.api.brasilApiCep.url}/01001000`).flush(backup());
      expect(street).toBe('Praça da Sé');
    });
  }

  it('bounds a slow primary request and uses the backup without another keystroke', fakeAsync(() => {
    let city: string = '';
    service.getAddressFromZipCode('01001000').subscribe(address => city = address.localidade);
    const first = httpTesting.expectOne(`${environment.api.viaCep.url}/01001000/json/`);
    tick(3000);
    expect(first.cancelled).toBeTrue();
    httpTesting.expectOne(`${environment.api.brasilApiCep.url}/01001000`).flush(backup());
    expect(city).toBe('São Paulo');
  }));

  it('returns control to manual entry when both providers stall', fakeAsync(() => {
    let failed: boolean = false;
    service.getAddressFromZipCode('01001000').subscribe({error: () => failed = true});
    httpTesting.expectOne(`${environment.api.viaCep.url}/01001000/json/`);
    tick(3000);
    const second = httpTesting.expectOne(`${environment.api.brasilApiCep.url}/01001000`);
    tick(8000);
    expect(second.cancelled).toBeTrue();
    expect(failed).toBeTrue();
  }));

  it('cancels the full chain when the postcode or view changes', fakeAsync(() => {
    const subscription = service.getAddressFromZipCode('01001000').subscribe();
    const first = httpTesting.expectOne(`${environment.api.viaCep.url}/01001000/json/`);
    subscription.unsubscribe(); tick(11000);
    expect(first.cancelled).toBeTrue();
    httpTesting.expectNone(`${environment.api.brasilApiCep.url}/01001000`);
  }));

  it('reuses a successful lookup for ten minutes without caching failures', fakeAsync(() => {
    service.getAddressFromZipCode('01001000').subscribe();
    httpTesting.expectOne(`${environment.api.viaCep.url}/01001000/json/`).flush(primary());
    let street: string = '';
    service.getAddressFromZipCode('01001-000').subscribe(address => street = address.logradouro);
    expect(street).toBe('Praça da Sé');
    httpTesting.expectNone(() => true);
    tick(600000);
    service.getAddressFromZipCode('01001000').subscribe({error: () => {}});
    httpTesting.expectOne(`${environment.api.viaCep.url}/01001000/json/`).flush({}, {status: 503, statusText: 'Unavailable'});
    httpTesting.expectOne(`${environment.api.brasilApiCep.url}/01001000`).flush({}, {status: 404, statusText: 'Not found'});
    service.getAddressFromZipCode('01001000').subscribe();
    httpTesting.expectOne(`${environment.api.viaCep.url}/01001000/json/`).flush(primary());
  }));

  it('accepts municipality-wide CEPs without inventing street or neighborhood information', () => {
    let street: string | undefined;
    service.getAddressFromZipCode('01001000').subscribe(address => street = address.logradouro);
    httpTesting.expectOne(`${environment.api.viaCep.url}/01001000/json/`).flush({...primary(), logradouro: '', bairro: ''});
    expect(street).toBe('');
    httpTesting.expectNone(`${environment.api.brasilApiCep.url}/01001000`);
  });

  it('rejects a mismatched backup response instead of filling the wrong address', () => {
    let failed: boolean = false;
    service.getAddressFromZipCode('01001000').subscribe({error: () => failed = true});
    httpTesting.expectOne(`${environment.api.viaCep.url}/01001000/json/`).flush({erro: true});
    httpTesting.expectOne(`${environment.api.brasilApiCep.url}/01001000`).flush({...backup(), cep: '22041001'});
    expect(failed).toBeTrue();
  });

});
