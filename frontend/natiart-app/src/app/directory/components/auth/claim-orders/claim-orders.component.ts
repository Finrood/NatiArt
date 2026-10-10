import {afterNextRender, Component, DestroyRef, ElementRef, inject, Injector, OnInit, signal, WritableSignal} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {ActivatedRoute, Router, RouterLink} from '@angular/router';
import {AbstractControl, FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {finalize} from 'rxjs';
import {GuestCheckoutService} from '../../../../product/service/guest-checkout.service';
import {TokenService} from '../../../service/token.service';
import {environment} from '../../../../../environments/environment';
import {CustomPasswordValidators} from '../../../validator/CustomPasswordValidators';
import {PasswordRequirementsComponent} from '../signup/password-requirements/password-requirements.component';
import {ButtonComponent} from '../../../../shared/components/button.component';
import {NatiartFormFieldComponent} from '../../../../shared/components/natiart-form-field/natiart-form-field.component';

interface ClaimInspection {email: string; existingVerifiedAccount: boolean}

@Component({selector: 'app-claim-orders', imports: [ReactiveFormsModule, RouterLink, PasswordRequirementsComponent, ButtonComponent, NatiartFormFieldComponent],
  template: `
  <main class="art-page"><div class="art-panel mx-auto max-w-xl p-6 sm:p-10">
    <h1 class="art-title mb-4" i18n>Your guest orders</h1>
    @if ($success()) {
      <p role="status" i18n>Your email is verified. Your guest orders are being connected to your account.</p>
      <a routerLink="/login" class="art-link" i18n>Sign in to view your orders</a>
    } @else if ($inspection()) {
      <p class="mb-4">{{ $inspection()!.email }}</p>
      @if ($inspection()!.existingVerifiedAccount) {
        <p class="mb-4" i18n>Enter your account password to connect your previous guest orders. Your saved account details stay the same.</p>
      } @else {
        <p class="mb-4" i18n>Choose a password to activate your account and save your guest orders. This replaces any earlier unverified password.</p>
      }
      <app-button [disabled]="$busy()" (click)="track()" [block]="true" i18n>View orders without creating an account</app-button>
      <p class="my-4 text-sm text-gray-600" i18n>You can also save your orders to an account for future visits.</p>
      <form [formGroup]="passwordForm" (ngSubmit)="confirm()" class="space-y-4">
        <app-natiart-form-field label="Password" i18n-label controlName="password" [form]="passwordForm" [isPassword]="true" inputId="claim-password">
          <input class="form-input" type="password" formControlName="password"
            [attr.autocomplete]="$inspection()!.existingVerifiedAccount ? 'current-password' : 'new-password'">
        </app-natiart-form-field>
        @if (!$inspection()!.existingVerifiedAccount) {
          <app-password-requirements [password]="passwordForm.controls.password.value"></app-password-requirements>
          <app-natiart-form-field label="Confirm password" i18n-label controlName="confirmation" [form]="passwordForm" [isPassword]="true" inputId="claim-confirmation">
            <input class="form-input" type="password" autocomplete="new-password" formControlName="confirmation">
          </app-natiart-form-field>
        }
        <app-button type="submit" [disabled]="$busy()" [block]="true" i18n>Save my orders to my account</app-button>
      </form>
    } @else {
      <p class="mb-4" i18n>Enter the email used at checkout. We will send a secure link to your guest orders.</p>
      <form [formGroup]="emailForm" (ngSubmit)="request()" class="space-y-4">
        <app-natiart-form-field label="Email" i18n-label controlName="email" [form]="emailForm" inputId="claim-email">
          <input class="form-input" type="email" autocomplete="email" formControlName="email">
        </app-natiart-form-field>
        <app-button type="submit" [disabled]="$busy()" [block]="true" i18n>Send secure link</app-button>
      </form>
      @if ($sent()) { <p class="mt-4" role="status" i18n>If guest details match this email, a link will arrive shortly. Check your inbox and spam folder.</p> }
    }
    @if ($error()) { <p class="mt-4 text-red-700" role="alert">{{ $error() }}</p> }
  </div></main>`})
export class ClaimOrdersComponent implements OnInit {
  private readonly _http: HttpClient = inject(HttpClient);
  private readonly _guest: GuestCheckoutService = inject(GuestCheckoutService);
  private readonly _tokens: TokenService = inject(TokenService);
  private readonly _route: ActivatedRoute = inject(ActivatedRoute);
  private readonly _router: Router = inject(Router);
  private readonly _destroy: DestroyRef = inject(DestroyRef);
  private readonly _fb: FormBuilder = inject(FormBuilder);
  private readonly _element: ElementRef<HTMLElement> = inject(ElementRef<HTMLElement>);
  private readonly _injector: Injector = inject(Injector);
  private token: string = '';
  readonly $inspection: WritableSignal<ClaimInspection | null> = signal<ClaimInspection | null>(null);
  readonly $busy: WritableSignal<boolean> = signal<boolean>(false);
  readonly $sent: WritableSignal<boolean> = signal<boolean>(false);
  readonly $success: WritableSignal<boolean> = signal<boolean>(false);
  readonly $error: WritableSignal<string> = signal<string>('');
  readonly emailForm: FormGroup<{email: FormControl<string>}> = this._fb.nonNullable.group({email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]]});
  readonly passwordForm: FormGroup<{password: FormControl<string>; confirmation: FormControl<string>}> = this._fb.nonNullable.group({password: ['', Validators.required], confirmation: ['']});
  ngOnInit(): void {
    const fragment: string = this._route.snapshot.fragment ?? '';
    const values: string[] = new URLSearchParams(fragment).getAll('token');
    this.token = values.length === 1 && /^[A-Za-z0-9_-]{43}$/.test(values[0]) ? values[0] : '';
    if (fragment) void this._router.navigate([], {relativeTo: this._route, fragment: undefined, replaceUrl: true});
    this.emailForm.controls.email.setValue(this._guest.$session()?.email ?? '');
    this._destroy.onDestroy((): void => { this.token = ''; this.passwordForm.reset(); });
    if (this.token) this._http.post<ClaimInspection>(environment.api.directory.url + '/checkout-claim/inspect',
      {token: this.token}, this._guest.options()).pipe(takeUntilDestroyed(this._destroy)).subscribe({
        next: (value: ClaimInspection): void => {
          if (!value.existingVerifiedAccount) {
            this.passwordForm.controls.password.addValidators(CustomPasswordValidators.passwordComplexity());
            this.passwordForm.controls.confirmation.addValidators(Validators.required);
            this.passwordForm.controls.password.updateValueAndValidity();
            this.passwordForm.controls.confirmation.updateValueAndValidity();
            this.passwordForm.addValidators((form: AbstractControl): {passwordMismatch: boolean} | null =>
              form.get('password')?.value === form.get('confirmation')?.value ? null : {passwordMismatch: true});
            this.passwordForm.updateValueAndValidity();
          }
          this.$inspection.set(value);
        },
        error: (): void => { this.token = ''; this.$error.set($localize`This link is invalid or expired. Request a new one.`); }
      });
  }
  request(): void {
    if (this.$busy()) return;
    if (this.emailForm.invalid) { this.emailForm.markAllAsTouched(); this.focusInvalid(); return; }
    this.$busy.set(true); this.$error.set('');
    this._guest.requestClaim(this.emailForm.controls.email.value).pipe(takeUntilDestroyed(this._destroy),
      finalize((): void => this.$busy.set(false))).subscribe({next: (): void => this.$sent.set(true),
      error: (): void => this.$error.set($localize`We could not send the link. Please retry shortly.`)});
  }
  track(): void {
    if (this.$busy() || !this.token || !this.$inspection()) return;
    this.$busy.set(true); this.$error.set('');
    this._http.post<void>(environment.api.directory.url + '/checkout-claim/track', {token: this.token}, this._guest.options())
      .pipe(takeUntilDestroyed(this._destroy), finalize((): void => this.$busy.set(false))).subscribe({
        next: (): void => { this.token = ''; this._guest.$active.set(false); this._guest.$tracking.set(true); void this._router.navigate(['/guest-orders']); },
        error: (): void => this.$error.set($localize`This link is invalid or expired. Request a new one.`)
      });
  }
  confirm(): void {
    if (this.$busy() || !this.token || !this.$inspection()) return;
    const existing: boolean = this.$inspection()!.existingVerifiedAccount;
    const password: string = this.passwordForm.controls.password.value;
    const confirmation: string = this.passwordForm.controls.confirmation.value;
    if (this.passwordForm.invalid) {
      this.passwordForm.markAllAsTouched();
      this.$error.set($localize`Check your password and confirmation.`); this.focusInvalid(); return;
    }
    this.$busy.set(true); this.$error.set('');
    this._http.post<void>(environment.api.directory.url + '/checkout-claim/confirm',
      {token: this.token, password, passwordConfirmation: existing ? password : confirmation}, this._guest.options())
      .pipe(takeUntilDestroyed(this._destroy), finalize((): void => this.$busy.set(false))).subscribe({
        next: (): void => { this.token = ''; this.passwordForm.reset(); this.$success.set(true);
          this._tokens.clearTokens(); this._guest.$active.set(false); this._guest.$session.set(null); },
        error: (): void => this.$error.set($localize`We could not activate or link the account. Check your password or request a new link.`)
      });
  }
  private focusInvalid(): void {
    afterNextRender((): void => this._element.nativeElement.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus(), {injector: this._injector});
  }
}
