import {ChangeDetectionStrategy, Component, inject, input, OnChanges, OnDestroy, OnInit} from '@angular/core';
import {AsyncPipe, CurrencyPipe} from '@angular/common';
import {HttpErrorResponse} from '@angular/common/http';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {BehaviorSubject, catchError, map, merge, Observable, of, startWith, Subject, switchMap, takeUntil, tap, timer} from 'rxjs';
import {ShippingBasketEstimateRequest, ShippingEstimate, ShippingService} from '../../../service/shipping.service';
import {CartItem} from '../../../models/CartItem.model';
import {CepFormatDirective} from '../../../../directory/directive/cep-format-directive.directive';
import {LoadingSpinnerComponent} from '../../../../shared/components/shared/loading-spinner/loading-spinner.component';
import {reportError} from '../../../../shared/service/error-reporting.service';

interface ShippingState {
  status: 'idle' | 'loading' | 'success' | 'error' | 'no-options';
  cheapestOption: ShippingEstimate | null;
  error: string | null;
}

@Component({
  selector: 'app-shipping-estimation',
  imports: [AsyncPipe, CurrencyPipe, ReactiveFormsModule, CepFormatDirective, LoadingSpinnerComponent],
  templateUrl: './shipping-estimation.component.html',
  styleUrls: ['./shipping-estimation.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ShippingEstimationComponent implements OnInit, OnChanges, OnDestroy {
  readonly $cartItems = input<CartItem[]>([], {alias: 'cartItems'});
  private readonly _fb: FormBuilder = inject(FormBuilder);
  private readonly _shippingService: ShippingService = inject(ShippingService);
  readonly shippingForm: FormGroup = this._fb.group({
    cep: ['', [Validators.required, Validators.pattern(/^\d{8}$/)]]
  });
  private readonly _state: BehaviorSubject<ShippingState> = new BehaviorSubject<ShippingState>(this.idle());
  readonly shippingState$: Observable<ShippingState> = this._state.asObservable();
  private readonly _destroyed$: Subject<void> = new Subject<void>();
  private readonly _refresh$: Subject<void> = new Subject<void>();

  ngOnInit(): void {
    const cepChanges: Observable<string | null> = this.shippingForm.get('cep')!.valueChanges.pipe(startWith(''));
    merge(cepChanges, this._refresh$.pipe(map((): string | null => this.shippingForm.get('cep')!.value))).pipe(
      tap((): void => this._state.next(this.idle())),
      switchMap((cep: string | null): Observable<ShippingState> => {
        if (!cep || this.shippingForm.get('cep')!.invalid || this.$cartItems().length === 0) return of(this.idle());
        return timer(300).pipe(
          tap((): void => this._state.next({status: 'loading', cheapestOption: null, error: null})),
          switchMap((): Observable<ShippingState> => this.estimateShipping(cep))
        );
      }),
      takeUntil(this._destroyed$)
    ).subscribe((state: ShippingState): void => this._state.next(state));
  }

  ngOnChanges(): void {
    this._refresh$.next();
  }

  retryEstimate(): void {
    this._refresh$.next();
  }

  ngOnDestroy(): void {
    this._destroyed$.next();
    this._destroyed$.complete();
    this._refresh$.complete();
  }

  private estimateShipping(cep: string): Observable<ShippingState> {
    const request: ShippingBasketEstimateRequest = {
      zipCode: cep,
      items: this.$cartItems().map((item: CartItem): ShippingBasketEstimateRequest['items'][number] => ({
        productId: item.product.id ?? '', quantity: item.quantity,
        personalized: !!(item.goldBorder || item.image || item.customImageUploadId || item.requiresArtworkReselection)
      }))
    };
    return this._shippingService.estimateBasket(request).pipe(
      map((options: ShippingEstimate[]): ShippingState => ({
        status: options.length ? 'success' : 'no-options',
        cheapestOption: options.length ? options.reduce((previous: ShippingEstimate, current: ShippingEstimate): ShippingEstimate =>
          previous.price <= current.price ? previous : current) : null,
        error: null
      })),
      catchError((error: HttpErrorResponse): Observable<ShippingState> => {
        reportError('shipping-estimation', error);
        return of({status: 'error', cheapestOption: null, error: $localize`Error fetching shipping estimates. Please try again.`});
      })
    );
  }

  private idle(): ShippingState {
    return {status: 'idle', cheapestOption: null, error: null};
  }
}
