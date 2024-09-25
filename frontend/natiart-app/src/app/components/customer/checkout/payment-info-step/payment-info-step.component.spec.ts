import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PaymentInfoStepComponent } from './payment-info-step.component';

describe('PaymentInfoStepComponent', () => {
  let component: PaymentInfoStepComponent;
  let fixture: ComponentFixture<PaymentInfoStepComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PaymentInfoStepComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PaymentInfoStepComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
