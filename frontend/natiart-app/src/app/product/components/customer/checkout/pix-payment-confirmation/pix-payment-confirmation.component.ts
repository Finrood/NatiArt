import {ChangeDetectorRef, Component, OnDestroy, OnInit} from '@angular/core';
import {exhaustMap, map} from "rxjs/operators";
import {catchError, interval, of, Subscription, throwError, timeout} from "rxjs";
import {PaymentService} from "../../../../service/payment.service";
import {ActivatedRoute, ParamMap, Router} from "@angular/router";
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
  qrCodeData: { encodedImage: string; payload: string; expirationDate: Date } | undefined;
  paymentStatus: string = 'PENDING';
  copyFailed: boolean = false;
  pollingInterval!: Subscription;
  private paramSubscription: Subscription | null = null;
  private qrSubscription: Subscription | null = null;
  private fireworksTimer: ReturnType<typeof setInterval> | null = null;
  private pollCount: number = 0;
  private readonly MAX_POLL_ATTEMPTS: number = 60;

  constructor(
    private route: ActivatedRoute,
    private paymentService: PaymentService,
    private router: Router,
    private changeDetectorRef: ChangeDetectorRef
  ) {
  }

  ngOnInit(): void {
    // Subscribe to param changes (not a one-shot snapshot): Angular reuses
    // this component when navigating between payment ids, and the QR lookup
    // plus status polling must follow the currently routed payment.
    this.paramSubscription = this.route.paramMap.subscribe((params: ParamMap): void => {
      const routedId: string | null = params.get('paymentId');
      this.stopPolling();
      this.stopQrCode();
      this.qrCodeData = undefined;
      this.copyFailed = false;
      if (routedId) {
        this.paymentId = routedId;
        this.paymentStatus = 'PENDING';
        this.loadQrCode(routedId);
        this.startPolling(routedId);
      } else {
        this.paymentId = null;
        this.paymentStatus = 'ERROR';
      }
    });
  }

  loadQrCode(paymentId: string) {
    this.stopQrCode();
    this.qrSubscription = this.paymentService.getPixQrCode(paymentId).subscribe(
      (data) => {
        if (!data.encodedImage || !data.payload || Number.isNaN(data.expirationDate.getTime())
          || data.expirationDate.getTime() <= Date.now()) {
          this.paymentStatus = 'EXPIRED';
          this.stopPolling();
          return;
        }
        this.qrCodeData = data;
        this.changeDetectorRef.detectChanges();
      },
      () => {
        // Status polling cannot make this screen usable without the QR code.
        // Stop it so a later PENDING status cannot replace the QR error with
        // the loading spinner while the component is already in a terminal
        // error state.
        this.stopPolling();
        this.paymentStatus = 'ERROR';
        this.changeDetectorRef.detectChanges();
      }
    );
  }

  stopQrCode(): void {
    if (this.qrSubscription) {
      this.qrSubscription.unsubscribe();
      this.qrSubscription = null;
    }
  }

  startPolling(paymentId: string) {
    let consecutiveErrors = 0;
    this.pollCount = 0;
    this.pollingInterval = interval(5000)
      .pipe(
        exhaustMap(() =>
          this.paymentService.getPaymentStatus(paymentId).pipe(
            timeout({each: 4500}),
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
          } else {
            consecutiveErrors = 0;
            this.paymentStatus = result.status;
            if (this.paymentStatus === 'COMPLETED') {
              this.triggerFireworks();
              this.stopPolling();
              return;
            }
            if (this.paymentStatus === 'EXPIRED' || this.isQrExpired()) {
              this.stopPolling();
              this.paymentStatus = 'EXPIRED';
              return;
            }
          }
          this.pollCount++;
          if (this.pollCount >= this.MAX_POLL_ATTEMPTS) {
            // Abandoned tab guard: every tick costs one upstream Asaas call
            // (N3), so a PENDING payment must not poll forever (~12 egress
            // calls/min). Stop and surface an error instead of polling
            // indefinitely; the user can revisit the page to resume.
            this.stopPolling();
            this.paymentStatus = 'ERROR';
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
      this.pollingInterval = undefined!;
    }
  }

  isQrExpired(): boolean {
    return !!this.qrCodeData && this.qrCodeData.expirationDate.getTime() <= Date.now();
  }

  retryPayment(): void {
    if (!this.paymentId) {
      return;
    }
    this.qrCodeData = undefined;
    this.paymentStatus = 'PENDING';
    this.loadQrCode(this.paymentId);
    this.startPolling(this.paymentId);
    this.changeDetectorRef.detectChanges();
  }

  copyToClipboard(inputElement: HTMLInputElement): void {
    this.copyFailed = false;
    inputElement.select();
    try {
      this.copyFailed = !document.execCommand('copy');
    } catch {
      this.copyFailed = true;
    } finally {
      inputElement.setSelectionRange(0, 0);
    }
  }

  closePayment() {
    this.router.navigate(['/']);
  }

  triggerFireworks() {
    this.stopFireworks();
    const duration = 5 * 1000; // 5 seconds
    const animationEnd = Date.now() + duration;
    const defaults = { startVelocity: 30, spread: 360, ticks: 60, zIndex: 0 };

    function randomInRange(min: number, max: number) {
      return Math.random() * (max - min) + min;
    }

    this.fireworksTimer = setInterval(() => {
      const timeLeft = animationEnd - Date.now();

      if (timeLeft <= 0) {
        this.stopFireworks();
        return;
      }

      const particleCount = 50 * (timeLeft / duration);
      // since particles fall down, start a bit higher than random
      confetti.default(Object.assign({}, defaults, { particleCount, origin: { x: randomInRange(0.1, 0.3), y: Math.random() - 0.2 } }));
      confetti.default(Object.assign({}, defaults, { particleCount, origin: { x: randomInRange(0.7, 0.9), y: Math.random() - 0.2 } }));
    }, 250);
  }

  stopFireworks(): void {
    if (this.fireworksTimer) {
      clearInterval(this.fireworksTimer);
      this.fireworksTimer = null;
    }
  }

  ngOnDestroy() {
    if (this.paramSubscription) {
      this.paramSubscription.unsubscribe();
      this.paramSubscription = null;
    }
    this.stopPolling();
    this.stopQrCode();
    this.stopFireworks();
  }
}
