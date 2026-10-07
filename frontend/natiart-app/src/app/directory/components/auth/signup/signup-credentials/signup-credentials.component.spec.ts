import {FormControl, FormGroup, Validators} from '@angular/forms';
import {CustomPasswordValidators} from '../../../../validator/CustomPasswordValidators';
import {DEFAULT_REQUIREMENTS} from '../../../../utils/password-utils';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { SignupCredentialsComponent } from './signup-credentials.component';

describe('SignupCredentialsComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SignupCredentialsComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('renders the shared policy and an accessible group mismatch through real input events', () => {
    const form = new FormGroup({username: new FormControl('art@example.com', [Validators.required, Validators.email]),
      password: new FormControl('', [Validators.required, CustomPasswordValidators.passwordComplexity()]),
      confirmPassword: new FormControl('', Validators.required)}, {validators: CustomPasswordValidators.passwordMatchValidator});
    const fixture = TestBed.createComponent(SignupCredentialsComponent);
    fixture.componentRef.setInput('credentialForm', form);
    fixture.detectChanges();
    function type(control: string, value: string): HTMLInputElement {
      const input: HTMLInputElement = fixture.nativeElement.querySelector(`input[formControlName="${control}"]`);
      input.value = value;
      input.dispatchEvent(new Event('input', {bubbles: true}));
      input.dispatchEvent(new Event('blur'));
      fixture.detectChanges();
      return input;
    }
    type('password', 'Abc123');
    expect(fixture.nativeElement.textContent).toContain(`Minimum ${DEFAULT_REQUIREMENTS.minLength} characters`);
    expect(fixture.nativeElement.querySelector('#' + fixture.nativeElement.querySelector('input[formControlName="password"]').getAttribute('aria-describedby')).textContent).toContain('at least 8 characters');
    expect(form.get('password')!.hasError('passwordComplexity')).toBeTrue();
    type('password', 'Abc12345');
    const confirm = type('confirmPassword', 'Different123');
    const error: HTMLElement = fixture.nativeElement.querySelector('#' + fixture.nativeElement.querySelector('input[formControlName="confirmPassword"]').getAttribute('aria-describedby'));
    expect(error.getAttribute('role')).toBe('alert');
    expect(error.textContent).toContain('Passwords do not match');
    expect(confirm.getAttribute('aria-describedby')).toBe(error.id);
    expect(confirm.getAttribute('aria-invalid')).toBe('true');
    expect(fixture.nativeElement.querySelector('button:not([aria-pressed])').disabled).toBeTrue();
    type('confirmPassword', 'Abc12345');
    expect(fixture.nativeElement.querySelector('#' + error.id)).toBeNull();
    expect(fixture.nativeElement.querySelector('button:not([aria-pressed])').disabled).toBeFalse();
    type('password', 'Ab1' + '😀'.repeat(18));
    expect(form.get('password')!.hasError('passwordTooLong')).toBeTrue();
    expect(fixture.nativeElement.querySelector('#' + fixture.nativeElement.querySelector('input[formControlName="password"]').getAttribute('aria-describedby')).textContent).toContain('Password is too long. Remove a few characters.');
    form.reset();
    expect(() => fixture.detectChanges()).not.toThrow();
    fixture.destroy();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(SignupCredentialsComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });
});
