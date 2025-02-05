import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SignupCredentialsComponent } from './signup-credentials.component';

describe('SignupCredentialsComponent', () => {
  let component: SignupCredentialsComponent;
  let fixture: ComponentFixture<SignupCredentialsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SignupCredentialsComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SignupCredentialsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
