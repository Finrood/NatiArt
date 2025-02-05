import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PasswordRequirementsComponent } from './password-requirements.component';

describe('PasswordRequirementsComponent', () => {
  let component: PasswordRequirementsComponent;
  let fixture: ComponentFixture<PasswordRequirementsComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PasswordRequirementsComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PasswordRequirementsComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
