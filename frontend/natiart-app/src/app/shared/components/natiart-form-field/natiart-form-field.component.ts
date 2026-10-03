import {ChangeDetectorRef, DestroyRef, inject, Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {merge} from 'rxjs';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {DEFAULT_REQUIREMENTS} from '../../../directory/utils/password-utils';
import {AbstractControl, FormGroup} from "@angular/forms";

import {ButtonComponent} from "../button.component";

@Component({
  selector: 'app-natiart-form-field',
  standalone: true,
  imports: [
    ButtonComponent
],
  templateUrl: './natiart-form-field.component.html',
  styleUrl: './natiart-form-field.component.css'
})
export class NatiartFormFieldComponent implements OnInit {
  @Input() label!: string;
  @Input() control!: AbstractControl | null;
  @Input() controlName!: string;
  @Input() form!: FormGroup;
  @Input() isPassword: boolean = false;

  readonly passwordRequirements = DEFAULT_REQUIREMENTS;
  private readonly _changeDetector = inject(ChangeDetectorRef);
  private readonly _destroyed = inject(DestroyRef);
  showPassword = false;
  @Output() showPasswordEmitter = new EventEmitter<void>();
  @Input() isOptional!: boolean;

  ngOnInit() {
    this.control = this.form.get(this.controlName);
    merge(this.form.events, ...(this.control ? [this.control.events] : [])).pipe(takeUntilDestroyed(this._destroyed))
      .subscribe((): void => this._changeDetector.markForCheck());
  }

  togglePasswordVisibility() {
    this.showPassword = !this.showPassword;
    this.showPasswordEmitter.emit();
  }

  hasPasswordMismatch(): boolean {
    return this.controlName === 'confirmPassword' && this.form.hasError('passwordMismatch');
  }

  showErrors(): boolean {
    return !!this.control && (this.control.invalid || this.hasPasswordMismatch()) && (this.control.dirty || this.control.touched);
  }
}
