import {ComponentFixture, TestBed} from '@angular/core/testing';

import {ShippingEstimationComponent} from './shipping-estimation.component';

describe('ShippingEstimationComponent', () => {
  let component: ShippingEstimationComponent;
  let fixture: ComponentFixture<ShippingEstimationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ShippingEstimationComponent]
    })
      .compileComponents();

    fixture = TestBed.createComponent(ShippingEstimationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
