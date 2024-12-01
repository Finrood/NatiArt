import { ComponentFixture, TestBed } from '@angular/core/testing';

import { PixPaymentConfirmationComponent } from './pix-payment-confirmation.component';

describe('PixPaymentConfirmationComponent', () => {
  let component: PixPaymentConfirmationComponent;
  let fixture: ComponentFixture<PixPaymentConfirmationComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PixPaymentConfirmationComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(PixPaymentConfirmationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
