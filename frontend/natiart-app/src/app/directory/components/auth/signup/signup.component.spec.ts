import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { FormControl, FormGroup } from '@angular/forms';
import { Subject } from 'rxjs';

import { SignupComponent } from './signup.component';
import { SignupService } from '../../../service/signup.service';
import { User } from '../../../models/user.model';

describe('SignupComponent', () => {
  let signupService: jasmine.SpyObj<SignupService>;

  beforeEach(async () => {
    signupService = jasmine.createSpyObj<SignupService>('SignupService', ['registerUser']);
    await TestBed.configureTestingModule({
      imports: [SignupComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {provide: SignupService, useValue: signupService},
      ],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(SignupComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('ignores duplicate submissions while registration is in flight', () => {
    const fixture = TestBed.createComponent(SignupComponent);
    const component: SignupComponent = fixture.componentInstance;
    component.signupForm = new FormGroup({
      credentials: new FormGroup({
        username: new FormControl('user@natiart.test'),
        password: new FormControl('Password123!'),
        confirmPassword: new FormControl('Password123!'),
      }),
      profile: new FormGroup({}),
    });
    const registration: Subject<User> = new Subject<User>();
    signupService.registerUser.and.returnValue(registration.asObservable());

    component.doRegisterUser();
    component.doRegisterUser();

    expect(signupService.registerUser).toHaveBeenCalledTimes(1);
    expect(component.isSubmitting).toBeTrue();

    registration.complete();

    expect(component.isSubmitting).toBeFalse();
  });
});
