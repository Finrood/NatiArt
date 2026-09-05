import { TestBed } from '@angular/core/testing';
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
      httpTesting.expectOne('https://viacep.test/ws/01001000/json/');
    } finally {
      environment.api.viaCep.url = original;
    }
  });

  it('normalizes formatted zip codes to digits', () => {
    service.getAddressFromZipCode('01001-000').subscribe();
    httpTesting.expectOne(`${environment.api.viaCep.url}/01001000/json/`);
  });

  it('rejects invalid zip codes before any HTTP call', () => {
    const seen: string[] = [];
    service.getAddressFromZipCode('abc').subscribe({error: (e: Error) => seen.push(e.message)});
    service.getAddressFromZipCode('123').subscribe({error: (e: Error) => seen.push(e.message)});

    expect(seen).toEqual(['Invalid zip code: expected 8 digits', 'Invalid zip code: expected 8 digits']);
    httpTesting.expectNone(() => true);
  });
});
