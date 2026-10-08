import {provideZonelessChangeDetection} from '@angular/core';
import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideHttpClient, HttpErrorResponse} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter} from '@angular/router';
import {Subject, of} from 'rxjs';
import {AccountDetailsComponent} from './account-details.component';
import {ChangePasswordComponent} from './change-password.component';
import {AuthenticationService} from '../../service/authentication.service';
import {TokenService} from '../../service/token.service';
import {Profile} from '../../models/profile.model';
import {RoleName, User} from '../../models/user.model';
import {environment} from '../../../../environments/environment';

const profile: Profile = {
  id: 'profile-1', version: 3, firstname: 'Ana', lastname: 'Silva', cpf: '12345678909', phone: '',
  country: 'Brazil', state: 'SP', city: 'São Paulo', neighborhood: 'Centro', zipCode: '01001000',
  street: 'Rua Principal', houseNumber: '123', complement: 'Apartment 4'
};
const user: User = {id: 'user-1', username: 'ana@example.test', role: RoleName.USER, externalId: '', profile};

function enter(fixture: ComponentFixture<unknown>, name: string, value: string): void {
  const input: HTMLInputElement = fixture.nativeElement.querySelector(`[formControlName="${name}"]`);
  input.value = value;
  input.dispatchEvent(new Event('input', {bubbles: true}));
}

describe('Account forms without a subsequent input event', () => {
  let auth: jasmine.SpyObj<AuthenticationService>;
  let load: Subject<User>;
  let update: Subject<Profile>;
  beforeEach(() => {
    localStorage.clear();
    load = new Subject<User>();
    update = new Subject<Profile>();
    auth = jasmine.createSpyObj<AuthenticationService>('AuthenticationService', ['fetchCurrentUser', 'updateProfile']);
    auth.fetchCurrentUser.and.returnValue(load);
    auth.updateProfile.and.returnValue(update);
    TestBed.configureTestingModule({
      imports: [AccountDetailsComponent, ChangePasswordComponent],
      providers: [provideZonelessChangeDetection(), provideHttpClient(), provideHttpClientTesting(), provideRouter([]),
        {provide: AuthenticationService, useValue: auth}]
    });
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify(); localStorage.clear(); });

  it('renders a retry when loading fails and never shows an empty editable profile', async () => {
    const fixture = TestBed.createComponent(AccountDetailsComponent);
    fixture.detectChanges();
    load.error(new Error('offline'));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('could not load');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
    auth.fetchCurrentUser.and.returnValue(of(user));
    fixture.nativeElement.querySelector('button').click();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[formControlName="houseNumber"]').value).toBe('123');
    expect(fixture.nativeElement.textContent).toContain('ana@example.test');
  });

  it('shows a deferred incorrect-password error immediately and preserves the address draft', async () => {
    const fixture = TestBed.createComponent(AccountDetailsComponent);
    fixture.detectChanges();
    load.next(user); load.complete();
    await fixture.whenStable();
    enter(fixture, 'houseNumber', '456'); enter(fixture, 'currentPassword', 'incorrect');
    await fixture.whenStable();
    fixture.nativeElement.querySelector('[type="submit"]').click();
    expect(auth.updateProfile).toHaveBeenCalledOnceWith(jasmine.objectContaining({version: 3, houseNumber: '456'}), 'incorrect');
    update.error(new HttpErrorResponse({status: 400, error: {code: 'CURRENT_PASSWORD_INCORRECT'}}));
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('current password is incorrect');
    expect(fixture.nativeElement.querySelector('[formControlName="houseNumber"]').value).toBe('456');
    expect(fixture.nativeElement.querySelector('[formControlName="currentPassword"]').value).toBe('');
    expect(fixture.componentInstance.$saving()).toBeFalse();
  });

  it('saves an updated version and shows confirmation immediately while preventing duplicate saves', async () => {
    const fixture = TestBed.createComponent(AccountDetailsComponent);
    fixture.detectChanges(); load.next(user); load.complete(); await fixture.whenStable();
    enter(fixture, 'complement', 'Apartment 7'); enter(fixture, 'currentPassword', 'OldPassword123');
    await fixture.whenStable();
    fixture.componentInstance.save(); fixture.componentInstance.save();
    expect(auth.updateProfile).toHaveBeenCalledTimes(1);
    update.next({...profile, version: 4, complement: 'Apartment 7'}); update.complete();
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('have been saved');
    expect(fixture.componentInstance.profileForm.pristine).toBeTrue();
    expect(fixture.nativeElement.querySelector('[type="submit"]').disabled).toBeTrue();
  });

  it('requires a house number on legacy profiles and leaves apartment details optional', async () => {
    const fixture = TestBed.createComponent(AccountDetailsComponent);
    fixture.detectChanges(); load.next({...user, profile: {...profile, houseNumber: undefined, complement: ''}}); load.complete();
    await fixture.whenStable();
    expect(fixture.componentInstance.profileForm.get('houseNumber')!.invalid).toBeTrue();
    expect(fixture.componentInstance.profileForm.get('complement')!.valid).toBeTrue();
    const number: HTMLInputElement = fixture.nativeElement.querySelector('[formControlName="houseNumber"]');
    expect(number.required).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('Apartment, suite or complement');
    expect(fixture.nativeElement.textContent).toContain('Enter N/A');
  });

  it('changes a password through the configured endpoint and clears credentials only after success', async () => {
    const fixture = TestBed.createComponent(ChangePasswordComponent);
    const tokens = TestBed.inject(TokenService);
    tokens.accessToken = 'test-access'; tokens.refreshToken = 'test-refresh';
    fixture.detectChanges();
    enter(fixture, 'currentPassword', 'OldPassword123'); enter(fixture, 'password', 'NewPassword456'); enter(fixture, 'confirmPassword', 'NewPassword456');
    await fixture.whenStable();
    fixture.nativeElement.querySelector('[type="submit"]').click();
    fixture.componentInstance.save();
    const request = TestBed.inject(HttpTestingController).expectOne(
      `${environment.api.directory.url}${environment.api.directory.endpoints.user}${environment.api.directory.endpoints.current}/change-password`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({currentPassword: 'OldPassword123', password: 'NewPassword456', passwordConfirmation: 'NewPassword456'});
    expect(tokens.accessToken).toBe('test-access');
    request.flush(null, {status: 204, statusText: 'No Content'});
    await fixture.whenStable();
    expect(tokens.accessToken).toBeNull(); expect(tokens.refreshToken).toBeNull();
    expect(fixture.nativeElement.querySelector('[role="status"]').textContent).toContain('password has been changed');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });

  it('shows password failure without logging out and rejects mismatched confirmation', async () => {
    const fixture = TestBed.createComponent(ChangePasswordComponent);
    const tokens = TestBed.inject(TokenService); tokens.accessToken = 'test-access';
    fixture.detectChanges();
    enter(fixture, 'currentPassword', 'wrong'); enter(fixture, 'password', 'NewPassword456'); enter(fixture, 'confirmPassword', 'Different123');
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[type="submit"]').disabled).toBeTrue();
    enter(fixture, 'confirmPassword', 'NewPassword456'); await fixture.whenStable();
    fixture.nativeElement.querySelector('[type="submit"]').click();
    const request = TestBed.inject(HttpTestingController).expectOne((r) => r.url.endsWith('/change-password'));
    request.flush({code: 'CURRENT_PASSWORD_INCORRECT'}, {status: 400, statusText: 'Bad Request'});
    await fixture.whenStable();
    expect(fixture.nativeElement.querySelector('[role="alert"]').textContent).toContain('current password is incorrect');
    expect(tokens.accessToken).toBe('test-access');
    expect(fixture.componentInstance.form.get('currentPassword')!.value).toBe('');
    expect(fixture.componentInstance.form.get('password')!.value).toBe('NewPassword456');
  });
});
