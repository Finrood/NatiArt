import {ChangeDetectorRef, Component, inject, signal, OnDestroy, OnInit} from '@angular/core';
import {exhaustMap, map} from "rxjs/operators";
import {catchError, interval, of, Subscription, take, takeUntil, throwError, timeout, timer} from "rxjs";
import {PaymentService} from "../../../../service/payment.service";
import {ActivatedRoute, ParamMap, Router} from "@angular/router";
import { DatePipe, NgClass } from "@angular/common";
import * as confetti from 'canvas-confetti';
import {CartService} from '../../../../service/cart.service';
import {AuthenticationService} from '../../../../../directory/service/authentication.service';
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
  readonly $orderId = signal<string | null>(null);
  readonly $cartUpdateFailed = signal(false);
  private resumeSubscription: Subscription | null = null;
  private expirySubscription: Subscription | null = null;
  pollingInterval!: Subscription;
  private paramSubscription: Subscription | null = null;
  private qrSubscription: Subscription | null = null;
  private fireworksTimer: ReturnType<typeof setInterval> | null = null;
  private pollCount: number = 0;
  private readonly MAX_POLL_ATTEMPTS: number = 60;

  private readonly _route = inject(ActivatedRoute);
  private readonly _paymentService = inject(PaymentService);
  private readonly _router = inject(Router);
  private readonly _changeDetectorRef = inject(ChangeDetectorRef);
  private readonly _cartService = inject(CartService);
  private readonly _authenticationService = inject(AuthenticationService);

  get paymentErrorMessage(): string {
    return this.paymentStatus === 'EXPIRED' ? $localize`This PIX code has expired.` : $localize`Could not load the payment details.`;
  }

  ngOnInit(): void {
    // Subscribe to param changes (not a one-shot snapshot): Angular reuses
    // this component when navigating between payment ids, and the QR lookup
    // plus status polling must follow the currently routed payment.
    this.paramSubscription = this._route.paramMap.subscribe((params: ParamMap): void => {
      const routedId: string | null = params.get('paymentId');
      this.stopPolling();
      this.stopQrCode();
      this.qrCodeData = undefined;
      this.$orderId.set(null);
      this.$cartUpdateFailed.set(false);
      this.stopFireworks();
      this.copyFailed = false;
      if (routedId) {
        this.paymentId = routedId;
        this.paymentStatus = 'PENDING';
        this.startPolling(routedId);
        this.loadQrCode(routedId);
      } else {
        this.paymentId = null;
        this.paymentStatus = 'ERROR';
      }
      this._changeDetectorRef.detectChanges();
    });
  }

  loadQrCode(paymentId: string): void {
    this.stopQrCode();
    this.qrSubscription = this._paymentService.getPixQrCode(paymentId).subscribe(
      (data) => {
        if (!data.encodedImage || !data.payload || Number.isNaN(data.expirationDate.getTime())
          || data.expirationDate.getTime() <= Date.now()) {
          this.paymentStatus = 'EXPIRED';
          this.stopPolling();
          this._changeDetectorRef.detectChanges();
          return;
        }
        this.qrCodeData = data;
        const remaining: number = data.expirationDate.getTime() - Date.now();
        if (remaining <= 300000) {
          this.expirySubscription = timer(remaining).subscribe((): void => {
            this.qrCodeData = undefined;
            this.paymentStatus = 'EXPIRED';
            this.stopPolling();
            this._changeDetectorRef.detectChanges();
          });
        }
        this._changeDetectorRef.detectChanges();
      },
      () => {
        // Status polling cannot make this screen usable without the QR code.
        // Stop it so a later PENDING status cannot replace the QR error with
        // the loading spinner while the component is already in a terminal
        // error state.
        this.stopPolling();
        this.paymentStatus = 'ERROR';
        this._changeDetectorRef.detectChanges();
      }
    );
  }

  stopQrCode(): void {
    this.resumeSubscription?.unsubscribe();
    this.resumeSubscription = null;
    this.expirySubscription?.unsubscribe();
    this.expirySubscription = null;
    if (this.qrSubscription) {
      this.qrSubscription.unsubscribe();
      this.qrSubscription = null;
    }
  }

  startPolling(paymentId: string): void {
    this.stopPolling();
    let consecutiveErrors = 0;
    this.pollCount = 0;
    this.pollingInterval = interval(5000)
      .pipe(
        exhaustMap(() =>
          this._paymentService.getPaymentStatus(paymentId).pipe(
            timeout({each: 10000}),
            map((status) => ({ok: true as const, status: status.status, orderId: status.orderId})),
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
        takeUntil(timer(300001)),
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
              this.completePayment(result.orderId);
              return;
            }
            if (this.paymentStatus === 'EXPIRED' || this.isQrExpired()) {
              this.stopPolling();
              this.paymentStatus = 'EXPIRED';
              this.qrCodeData = undefined;
              this._changeDetectorRef.detectChanges();
              return;
            }
          }
          this._changeDetectorRef.detectChanges();
          this.pollCount++;
          if (this.pollCount >= this.MAX_POLL_ATTEMPTS) {
            // Abandoned tab guard: every tick costs one upstream Asaas call
            // (N3), so a PENDING payment must not poll forever (~12 egress
            // calls/min). Stop and surface an error instead of polling
            // indefinitely; the user can revisit the page to resume.
            this.stopPolling();
            this.paymentStatus = 'ERROR';
            this._changeDetectorRef.detectChanges();
          }
        },
        error: () => {
          // 5 consecutive errors: stop polling and surface a non-success state instead of dying silently.
          this.stopPolling();
          this.paymentStatus = 'ERROR';
          this._changeDetectorRef.detectChanges();
        },
        complete: (): void => {
          if (this.paymentStatus === 'PENDING') {
            this.paymentStatus = 'ERROR';
            this.stopQrCode();
            this._changeDetectorRef.detectChanges();
          }
        },
      });
  }

  stopPolling(): void {
    if (this.pollingInterval) {
      this.pollingInterval.unsubscribe();
      this.pollingInterval = undefined!;
    }
  }

  isQrExpired(): boolean {
    return !!this.qrCodeData && this.qrCodeData.expirationDate.getTime() <= Date.now();
  }

  retryPayment(): void {
    const paymentId: string | null = this.paymentId;
    if (!paymentId) return;
    this.stopPolling();
    this.stopQrCode();
    this.qrCodeData = undefined;
    this.paymentStatus = 'PENDING';
    this.resumeSubscription = this._paymentService.getPaymentStatus(paymentId).pipe(timeout(10000)).subscribe({
      next: (status): void => {
        if (status.status === 'COMPLETED') {
          this.completePayment(status.orderId);
        } else {
          this.startPolling(paymentId);
          this.loadQrCode(paymentId);
        }
      },
      error: (): void => {
        this.paymentStatus = 'ERROR';
        this._changeDetectorRef.detectChanges();
      },
    });
    this._changeDetectorRef.detectChanges();
  }

  private completePayment(orderId: string | undefined): void {
    this.paymentStatus = 'COMPLETED';
    this.$orderId.set(orderId ?? null);
    this.stopPolling();
    this.stopQrCode();
    this.qrCodeData = undefined;
    if (orderId) {
      this._authenticationService.currentUser$.pipe(take(1)).subscribe((user): void => {
        if (!user?.externalId) return;
        try { this._cartService.completePurchase(orderId, user.externalId); }
        catch { this.$cartUpdateFailed.set(true); }
      });
    }
    this.triggerFireworks();
    this._changeDetectorRef.detectChanges();
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

  closePayment(): void {
    this._router.navigate(['/']);
  }

  triggerFireworks(): void {
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

  ngOnDestroy(): void {
    if (this.paramSubscription) {
      this.paramSubscription.unsubscribe();
      this.paramSubscription = null;
    }
    this.stopPolling();
    this.stopQrCode();
    this.stopFireworks();
  }
}
