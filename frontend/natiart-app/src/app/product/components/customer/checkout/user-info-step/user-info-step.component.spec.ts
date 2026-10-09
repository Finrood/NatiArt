import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { FormControl, FormGroup } from '@angular/forms';

import { UserInfoStepComponent } from './user-info-step.component';

describe('UserInfoStepComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserInfoStepComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(UserInfoStepComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  function render(accountCheckout: boolean) {
    const fixture = TestBed.createComponent(UserInfoStepComponent);
    const userInfo = new FormGroup({
      firstname: new FormControl(''), lastname: new FormControl(''),
      email: new FormControl(''), cpf: new FormControl(''), phone: new FormControl(''),
    });
    fixture.componentRef.setInput('checkoutForm', new FormGroup({userInfo}));
    fixture.componentRef.setInput('accountCheckout', accountCheckout);
    fixture.detectChanges();
    return {fixture, userInfo};
  }

  it('lets guests enter their CPF without an account-only hint', () => {
    const {fixture, userInfo} = render(false);
    const cpf: HTMLInputElement = fixture.nativeElement.querySelector('input[inputmode="numeric"]');
    expect(cpf.readOnly).toBeFalse();
    cpf.value = '529.982.247-25';
    cpf.dispatchEvent(new Event('input'));
    expect(userInfo.controls.cpf.value).toBe('52998224725');
    expect(cpf.value).toBe('529.982.247-25');
    expect(fixture.nativeElement.querySelector('a')).toBeNull();
  });

  it('keeps account CPF read-only with a link to update account details', () => {
    const {fixture} = render(true);
    expect(fixture.nativeElement.querySelector('input[inputmode="numeric"]').readOnly).toBeTrue();
    expect(fixture.nativeElement.querySelector('a').getAttribute('href')).toBe('/account/profile');
  });
});
