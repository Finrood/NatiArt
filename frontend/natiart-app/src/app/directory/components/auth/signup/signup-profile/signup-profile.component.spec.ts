import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SignupProfileComponent } from './signup-profile.component';

describe('SignupProfileComponent', () => {
  let component: SignupProfileComponent;
  let fixture: ComponentFixture<SignupProfileComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [SignupProfileComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(SignupProfileComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
