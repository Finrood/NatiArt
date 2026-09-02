import {Component, OnDestroy, OnInit} from '@angular/core';
import {map, switchMap} from "rxjs/operators";
import {catchError, interval, of, Subscription, throwError} from "rxjs";
import {PaymentService} from "../../../../service/payment.service";
import {ActivatedRoute, Router} from "@angular/router";
import { DatePipe, NgClass } from "@angular/common";
import * as confetti from 'canvas-confetti';
import {ButtonComponent} from "../../../../../shared/components/button.component";

@Component({
  selector: 'app-pix-payment-confirmation',
  imports: [
    DatePipe,
    NgClass,
    ButtonComponent
],
  templateUrl: './pix-payment-confirmation.component.html',
})
export class PixPaymentConfirmationComponent implements OnInit, OnDestroy {
  paymentId: string | null = null;
  qrCodeData!: { encodedImage: string; payload: string; expirationDate: Date };
  paymentStatus: string = 'PENDING';
  pollingInterval!: Subscription;

  constructor(
    private route: ActivatedRoute,
    private paymentService: PaymentService,
    private router: Router
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
    let consecutiveErrors = 0;
    this.pollingInterval = interval(5000)
      .pipe(
        switchMap(() =>
          this.paymentService.getPaymentStatus(paymentId).pipe(
            map((status) => ({ok: true as const, status: status.status})),
            catchError((error) => {
              consecutiveErrors++;
              if (consecutiveErrors >= 5) {
                // Give up only after 5 consecutive failures (surfaced via the error handler below).
                return throwError(() => error);
              }
              // Transient failure (network hiccup, 5xx): do NOT kill the polling chain.
              return of({ok: false as const});
            }),
          ),
        ),
      )
      .subscribe({
        next: (result) => {
          if (!result.ok) {
            // Keep the previous PENDING visual state during transient failures.
            this.paymentStatus = 'PENDING';
            return;
          }
          consecutiveErrors = 0;
          this.paymentStatus = result.status;
          if (this.paymentStatus === 'COMPLETED') {
            this.triggerFireworks();
            this.stopPolling();
          }
        },
        error: () => {
          // 5 consecutive errors: stop polling and surface a non-success state instead of dying silently.
          this.stopPolling();
          this.paymentStatus = 'ERROR';
        },
      });
  }

  stopPolling() {
    if (this.pollingInterval) {
      this.pollingInterval.unsubscribe();
    }
  }

  copyToClipboard(inputElement: HTMLInputElement) {
    inputElement.select();
    document.execCommand('copy');
    inputElement.setSelectionRange(0, 0);
  }

  closePayment() {
    this.router.navigate(['/']);
  }

  triggerFireworks() {
    const duration = 5 * 1000; // 5 seconds
    const animationEnd = Date.now() + duration;
    const defaults = { startVelocity: 30, spread: 360, ticks: 60, zIndex: 0 };

    function randomInRange(min: number, max: number) {
      return Math.random() * (max - min) + min;
    }

    const intervalConfetti = setInterval(() => {
      const timeLeft = animationEnd - Date.now();

      if (timeLeft <= 0) {
        return clearInterval(intervalConfetti);
      }

      const particleCount = 50 * (timeLeft / duration);
      // since particles fall down, start a bit higher than random
      confetti.default(Object.assign({}, defaults, { particleCount, origin: { x: randomInRange(0.1, 0.3), y: Math.random() - 0.2 } }));
      confetti.default(Object.assign({}, defaults, { particleCount, origin: { x: randomInRange(0.7, 0.9), y: Math.random() - 0.2 } }));
    }, 250);
  }

  ngOnDestroy() {
    this.stopPolling();
  }
}

