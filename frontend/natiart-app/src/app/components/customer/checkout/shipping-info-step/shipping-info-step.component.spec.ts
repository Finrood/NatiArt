import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ShippingInfoStepComponent } from './shipping-info-step.component';

describe('ShippingInfoStepComponent', () => {
  let component: ShippingInfoStepComponent;
  let fixture: ComponentFixture<ShippingInfoStepComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShippingInfoStepComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ShippingInfoStepComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
