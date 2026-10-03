import {afterEveryRender, ChangeDetectorRef, DestroyRef, ElementRef, inject, signal, Component, EventEmitter, Input, OnInit, Output} from '@angular/core';
import {merge} from 'rxjs';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {AbstractControl, FormGroup, Validators} from "@angular/forms";

import {ButtonComponent} from "../button.component";

@Component({
  selector: 'app-natiart-form-field',
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

  private static nextId: number = 0;
  private readonly generatedId: string = 'natiart-field-' + NatiartFormFieldComponent.nextId++;
  private readonly _element = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly _changeDetector = inject(ChangeDetectorRef);
  private readonly _destroyed = inject(DestroyRef);
  private nativeControl: HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement | null = null;
  private originalDescription: string = '';
  readonly $showPassword = signal(false);
  get showPassword(): boolean { return this.$showPassword(); }

  constructor() { afterEveryRender((): void => this.syncControl()); }

  @Output() showPasswordEmitter = new EventEmitter<void>();
  @Input() isOptional!: boolean;
  @Input() inputId: string | null = null;

  get resolvedInputId(): string {
    return this.inputId || this.generatedId;
  }

  ngOnInit(): void {
    this.control = this.form.get(this.controlName);
    merge(this.form.events, ...(this.control ? [this.control.events] : [])).pipe(takeUntilDestroyed(this._destroyed))
      .subscribe((): void => this._changeDetector.markForCheck());
  }

  get passwordVisibilityLabel(): string { return this.showPassword ? $localize`Hide password` : $localize`Show password`; }

  togglePasswordVisibility(): void {
    this.$showPassword.update((visible: boolean): boolean => !visible);
    if (this.nativeControl instanceof HTMLInputElement) this.nativeControl.type = this.showPassword ? 'text' : 'password';
    this.showPasswordEmitter.emit();
  }

  private syncControl(): void {
    if (!this.nativeControl) {
      this.nativeControl = this._element.nativeElement.querySelector('input,select,textarea');
      this.originalDescription = this.nativeControl?.getAttribute('aria-describedby') || '';
    }
    const input: HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement | null = this.nativeControl;
    if (!input) return;
    input.id = this.resolvedInputId;
    input.required = !!this.control?.hasValidator(Validators.required);
    input.setAttribute('aria-required', String(input.required));
    input.setAttribute('aria-invalid', String(this.showErrors()));
    const description: string = [this.originalDescription, this.showErrors() ? this.resolvedInputId + '-error' : ''].filter(Boolean).join(' ');
    if (description) input.setAttribute('aria-describedby', description);
    else input.removeAttribute('aria-describedby');
  }

  showErrors(): boolean {
    return !!this.control && this.control.invalid && (this.control.dirty || this.control.touched);
  }
}
