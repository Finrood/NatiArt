import {Component} from '@angular/core';
import {TestBed} from '@angular/core/testing';
import {FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {NatiartFormFieldComponent} from './natiart-form-field.component';

@Component({imports: [NatiartFormFieldComponent, ReactiveFormsModule], template: `
  <span id="hint">Address hint</span><div [formGroup]="form">
    <app-natiart-form-field label="Shipping street" controlName="street" [form]="form" inputId="shipping-street"><input formControlName="street" aria-describedby="hint"></app-natiart-form-field>
    <app-natiart-form-field label="Billing street" controlName="street" [form]="form" inputId="billing-street"><input formControlName="street"></app-natiart-form-field>
    <app-natiart-form-field label="Password" controlName="password" [form]="form" [isPassword]="true"><input type="password" formControlName="password"></app-natiart-form-field>
  </div>`})
class FieldJourney {
  readonly form = new FormGroup({street: new FormControl('', Validators.required), password: new FormControl('Secret123')});
}

describe('Projected native control accessibility', () => {
  beforeEach(async () => {await TestBed.configureTestingModule({imports: [FieldJourney]}).compileComponents();});
  it('links unique labels/error descriptions and forwards invalid/required state without losing hints', () => {
    const fixture = TestBed.createComponent(FieldJourney);
    fixture.detectChanges();
    const inputs: NodeListOf<HTMLInputElement> = fixture.nativeElement.querySelectorAll('input');
    const labels: NodeListOf<HTMLLabelElement> = fixture.nativeElement.querySelectorAll('label');
    expect(new Set(Array.from(inputs).map((input) => input.id)).size).toBe(3);
    for (let i = 0; i < 2; i++) {
      labels[i].click();
      expect(document.activeElement).toBe(inputs[i]);
      expect(inputs[i].required).toBeTrue();
      expect(inputs[i].getAttribute('aria-required')).toBe('true');
    }
    inputs[0].dispatchEvent(new Event('blur'));
    fixture.componentInstance.form.get('street')!.markAsTouched();
    fixture.detectChanges();
    expect(inputs[0].getAttribute('aria-invalid')).toBe('true');
    expect(inputs[0].getAttribute('aria-describedby')).toBe('hint shipping-street-error');
    expect(fixture.nativeElement.querySelector('#shipping-street-error').getAttribute('role')).toBe('alert');
    inputs[0].value = 'Rua da Arte';
    inputs[0].dispatchEvent(new Event('input', {bubbles: true}));
    fixture.detectChanges();
    expect(inputs[0].getAttribute('aria-invalid')).toBe('false');
    expect(inputs[0].getAttribute('aria-describedby')).toBe('hint');
    fixture.destroy();
  });
  it('names the password toggle and forwards its pressed state to the native button', () => {
    const fixture = TestBed.createComponent(FieldJourney);
    fixture.detectChanges();
    const toggle: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    const password: HTMLInputElement = fixture.nativeElement.querySelector('input[type="password"]');
    expect(toggle.getAttribute('aria-label')).toBe('Show password');
    expect(toggle.getAttribute('aria-pressed')).toBe('false');
    toggle.click();
    fixture.detectChanges();
    expect(password.type).toBe('text');
    expect(toggle.getAttribute('aria-label')).toBe('Hide password');
    expect(toggle.getAttribute('aria-pressed')).toBe('true');
    fixture.destroy();
  });
});
