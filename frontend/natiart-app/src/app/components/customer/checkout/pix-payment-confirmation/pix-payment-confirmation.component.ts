import {Component, OnDestroy, OnInit} from '@angular/core';
import {switchMap} from "rxjs/operators";
import {interval, Subscription} from "rxjs";
import {PaymentService} from "../../../../service/payment.service";
import {ActivatedRoute} from "@angular/router";
import {DatePipe, NgIf} from "@angular/common";

@Component({
  selector: 'app-pix-payment-confirmation',
  standalone: true,
  imports: [
    DatePipe,
    NgIf
  ],
  templateUrl: './pix-payment-confirmation.component.html',
  styleUrl: './pix-payment-confirmation.component.css'
})
export class PixPaymentConfirmationComponent implements OnInit, OnDestroy {
  paymentId: string | null = null;
  qrCodeData!: { encodedImage: string; payload: string; expirationDate: Date };
  paymentStatus: string = 'Pending';
  pollingInterval!: Subscription;

  constructor(
    private route: ActivatedRoute,
    private paymentService: PaymentService
  ) {
  }

  ngOnInit(): void {
    this.paymentId = this.route.snapshot.paramMap.get('paymentId');

    if (this.paymentId) {
      this.loadQrCode(this.paymentId);
      this.startPolling(this.paymentId);
    }
  }

  loadQrCode(paymentId: string) {
    this.paymentService.getPixQrCode(paymentId).subscribe(
      (data) => (this.qrCodeData = data),
      (error) => console.error('Error fetching QR code:', error)
    );
  }

  startPolling(paymentId: string) {
    this.pollingInterval = interval(5000)
      .pipe(switchMap(() => this.paymentService.getPaymentStatus(paymentId)))
      .subscribe((status) => {
        this.paymentStatus = status.status;
        if (status === 'PAID') {
          this.stopPolling();
        }
      });
  }

  stopPolling() {
    if (this.pollingInterval) {
      this.pollingInterval.unsubscribe();
    }
  }

  ngOnDestroy() {
    this.stopPolling();
  }
}
