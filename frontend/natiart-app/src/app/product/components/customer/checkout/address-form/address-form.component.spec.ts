import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subject, of, throwError } from 'rxjs';

import { AddressFormComponent } from './address-form.component';
import { SignupService } from '../../../../../directory/service/signup.service';
import { ViaCEPResponse } from '../../../../../directory/models/viaCEPResponse.model';
import { CustomCepValidators } from '../../../../../directory/validator/CustomCepValidators';

function makeAddressForm(fb: FormBuilder): FormGroup {
  return fb.group({
    zipCode: ['', [Validators.required, CustomCepValidators.validCep()]],
    street: ['', Validators.required],
    city: ['', Validators.required],
    neighborhood: ['', Validators.required],
    state: ['', Validators.required],
    country: ['Brazil', Validators.required],
    houseNumber: ['', Validators.required],
    complement: ['']
  });
}

function viaCepResponse(street: string): ViaCEPResponse {
  return {
    cep: '01001000',
    logradouro: street,
    complemento: '',
    bairro: 'Se',
    localidade: 'Sao Paulo',
    uf: 'SP',
    ibge: '',
    gia: '',
    ddd: '',
    siafi: ''
  };
}

describe('AddressFormComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AddressFormComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('renders the house-number limit and no-number instruction', () => {
    const fixture = TestBed.createComponent(AddressFormComponent);
    fixture.componentInstance.addressFormGroup = makeAddressForm(TestBed.inject(FormBuilder));
    fixture.detectChanges();
    const input: HTMLInputElement = fixture.nativeElement.querySelector('[formControlName="houseNumber"]');
    expect(input.maxLength).toBe(255);
    expect(fixture.nativeElement.textContent).toContain('Enter N/A');
    fixture.destroy();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(AddressFormComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('debounces rapid typing into a single address lookup', fakeAsync(() => {
    const signupService: SignupService = TestBed.inject(SignupService);
    const lookupSpy = spyOn(signupService, 'getAddressFromZipCode').and.returnValue(of(viaCepResponse('Praca da Se')));
    const fb: FormBuilder = TestBed.inject(FormBuilder);
    const fixture: ComponentFixture<AddressFormComponent> = TestBed.createComponent(AddressFormComponent);
    fixture.componentInstance.addressFormGroup = makeAddressForm(fb);
    fixture.detectChanges();

    const zip = fixture.componentInstance.addressFormGroup.get('zipCode')!;
    zip.setValue('01001');
    tick(100);
    zip.setValue('010010');
    tick(100);
    zip.setValue('01001000');
    expect(lookupSpy).not.toHaveBeenCalled();
    tick(400);

    expect(lookupSpy).toHaveBeenCalledTimes(1);
    expect(lookupSpy).toHaveBeenCalledWith('01001000');
    fixture.destroy();
  }));

  for (const failure of ['not found', 'network error'] as const) {
    it(`allows a complete manual address after a CEP ${failure}`, fakeAsync(() => {
      const signupService = TestBed.inject(SignupService);
      spyOn(signupService, 'getAddressFromZipCode').and.returnValue(
        failure === 'not found'
          ? of({...viaCepResponse(''), erro: true})
          : throwError(() => new Error('offline'))
      );
      const fixture = TestBed.createComponent(AddressFormComponent);
      const form = makeAddressForm(TestBed.inject(FormBuilder));
      fixture.componentInstance.addressFormGroup = form;
      fixture.detectChanges();

      form.get('zipCode')!.setValue('01001000');
      tick(400);
      expect(form.get('zipCode')!.valid).toBeTrue();
      expect(form.get('country')!.value).toBe('Brazil');
      expect(fixture.componentInstance.errorMessage).toContain('manually');

      form.patchValue({
        state: 'SP', city: 'Sao Paulo', neighborhood: 'Centro',
        street: 'Rua Manual', houseNumber: '10'
      });
      expect(form.valid).toBeTrue();
      fixture.destroy();
    }));
  }

  it('drops a stale lookup response when a newer CEP is entered', fakeAsync(() => {
    const first$: Subject<ViaCEPResponse> = new Subject<ViaCEPResponse>();
    const second$: Subject<ViaCEPResponse> = new Subject<ViaCEPResponse>();
    const signupService: SignupService = TestBed.inject(SignupService);
    const lookupSpy = spyOn(signupService, 'getAddressFromZipCode').and.returnValues(first$, second$);
    const fb: FormBuilder = TestBed.inject(FormBuilder);
    const fixture: ComponentFixture<AddressFormComponent> = TestBed.createComponent(AddressFormComponent);
    fixture.componentInstance.addressFormGroup = makeAddressForm(fb);
    fixture.detectChanges();

    const form: FormGroup = fixture.componentInstance.addressFormGroup;
    form.get('zipCode')!.setValue('11111111');
    tick(400);
    expect(lookupSpy).toHaveBeenCalledTimes(1);

    form.get('zipCode')!.setValue('22222222');
    // Cancel immediately on the value change, even before the next debounce expires.
    first$.next({...viaCepResponse('Rua Antiga'), erro: true});
    tick(0);
    expect(form.get('street')!.value).not.toBe('Rua Antiga');
    tick(400);
    expect(lookupSpy).toHaveBeenCalledTimes(2);

    second$.next(viaCepResponse('Rua Nova'));
    tick(0);

    expect(form.get('street')!.value).toBe('Rua Nova');
    fixture.destroy();
  }));
});
