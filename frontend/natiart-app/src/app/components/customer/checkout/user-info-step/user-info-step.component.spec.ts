import { ComponentFixture, TestBed } from '@angular/core/testing';

import { UserInfoStepComponent } from './user-info-step.component';

describe('UserInfoStepComponent', () => {
  let component: UserInfoStepComponent;
  let fixture: ComponentFixture<UserInfoStepComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UserInfoStepComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(UserInfoStepComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
