import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { provideAnimations } from '@angular/platform-browser/animations';
import { FormBuilder, FormGroup } from '@angular/forms';
import { Subject, of } from 'rxjs';

import { AddressFormComponent } from './address-form.component';
import { SignupService } from '../../../../../directory/service/signup.service';
import { ViaCEPResponse } from '../../../../../directory/models/viaCEPResponse.model';

function makeAddressForm(fb: FormBuilder): FormGroup {
  return fb.group({
    zipCode: [''],
    street: [''],
    city: [''],
    neighborhood: [''],
    state: [''],
    country: [''],
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
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([]), provideAnimations()],
    }).compileComponents();
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
    tick(400);
    expect(lookupSpy).toHaveBeenCalledTimes(2);

    // The stale first response arrives after the second lookup started: ignored.
    first$.next({...viaCepResponse('Rua Antiga'), erro: true});
    tick(0);
    second$.next(viaCepResponse('Rua Nova'));
    tick(0);

    expect(form.get('street')!.value).toBe('Rua Nova');
    fixture.destroy();
  }));
});
