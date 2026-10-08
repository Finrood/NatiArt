import {Component, DestroyRef, inject, OnDestroy, OnInit, signal, WritableSignal} from '@angular/core';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {HttpErrorResponse} from '@angular/common/http';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {finalize} from 'rxjs';
import {Profile} from '../../models/profile.model';
import {User} from '../../models/user.model';
import {AuthenticationService} from '../../service/authentication.service';
import {accountErrorMessage} from '../../service/account.service';
import {createProfileForm} from '../../../shared/forms/profile-form';
import {ProfileFormFieldsComponent} from '../../../shared/components/profile-form-fields.component';
import {NatiartFormFieldComponent} from '../../../shared/components/natiart-form-field/natiart-form-field.component';

@Component({
  selector: 'app-account-details',
  imports: [ReactiveFormsModule, ProfileFormFieldsComponent, NatiartFormFieldComponent],
  templateUrl: './account-details.component.html'
})
export class AccountDetailsComponent implements OnInit, OnDestroy {
  private readonly _fb: FormBuilder = inject(FormBuilder);
  private readonly _auth: AuthenticationService = inject(AuthenticationService);
  private readonly _destroy: DestroyRef = inject(DestroyRef);
  readonly form: FormGroup = this._fb.nonNullable.group({
    profile: createProfileForm(this._fb),
    currentPassword: ['', [Validators.required, Validators.maxLength(256)]]
  });
  readonly $loading: WritableSignal<boolean> = signal(true);
  readonly $saving: WritableSignal<boolean> = signal(false);
  readonly $error: WritableSignal<string> = signal('');
  readonly $saved: WritableSignal<boolean> = signal(false);
  readonly $email: WritableSignal<string> = signal('');
  readonly $profileLoaded: WritableSignal<boolean> = signal(false);
  private loaded: Profile | null = null;

  get profileForm(): FormGroup {
    return this.form.get('profile') as FormGroup;
  }

  ngOnInit(): void {
    this.profileForm.valueChanges.pipe(takeUntilDestroyed(this._destroy))
      .subscribe((): void => this.$saved.set(false));
    this.reload();
  }

  reload(): void {
    if (this.$saving()) return;
    this.$loading.set(true);
    this.$profileLoaded.set(false);
    this.$error.set('');
    this.$saved.set(false);
    this._auth.fetchCurrentUser()
      .pipe(takeUntilDestroyed(this._destroy), finalize((): void => this.$loading.set(false)))
      .subscribe({
        next: (user: User): void => {
          this.loaded = user.profile;
          this.$profileLoaded.set(true);
          this.$email.set(user.username);
          this.profileForm.reset(user.profile, {emitEvent: false});
          this.form.get('currentPassword')!.reset();
        },
        error: (): void => this.$error.set($localize`:@@accountLoadError:We could not load your details. Please try again.`)
      });
  }

  save(): void {
    if (this.$saving() || this.$loading() || !this.loaded || !this.profileForm.dirty) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.$saving.set(true);
    this.$error.set('');
    this.$saved.set(false);
    const profile: Profile = {...this.profileForm.getRawValue(), version: this.loaded.version};
    this._auth.updateProfile(profile, this.form.get('currentPassword')!.value)
      .pipe(
        takeUntilDestroyed(this._destroy),
        finalize((): void => {
          this.$saving.set(false);
          this.form.get('currentPassword')!.reset();
        })
      )
      .subscribe({
        next: (saved: Profile): void => {
          this.loaded = saved;
          this.profileForm.reset(saved, {emitEvent: false});
          this.$saved.set(true);
        },
        error: (error: HttpErrorResponse): void => this.$error.set(accountErrorMessage(error))
      });
  }

  ngOnDestroy(): void {
    this.form.get('currentPassword')!.reset();
  }
}
