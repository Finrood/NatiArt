import {ChangeDetectionStrategy, Component, OnDestroy, OnInit} from '@angular/core';
import { AsyncPipe } from '@angular/common';
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {BehaviorSubject, Observable, Subject} from 'rxjs';
import {
  catchError,
  debounceTime,
  distinctUntilChanged,
  filter,
  map,
  startWith,
  switchMap,
  takeUntil,
  tap
} from 'rxjs/operators';
import {ShippingEstimate, ShippingService} from '../../../service/shipping.service';
import {CepFormatDirective} from "../../../../directory/directive/cep-format-directive.directive";
import {LoadingSpinnerComponent} from "../../../../shared/components/shared/loading-spinner/loading-spinner.component";

interface ShippingState {
  status: 'idle' | 'loading' | 'success' | 'error' | 'no-options';
  cheapestOption: ShippingEstimate | null;
  error: string | null;
}

@Component({
    selector: 'app-shipping-estimation',
    imports: [AsyncPipe, ReactiveFormsModule, CepFormatDirective, LoadingSpinnerComponent],
    templateUrl: './shipping-estimation.component.html',
    styleUrls: ['./shipping-estimation.component.css'],
    changeDetection: ChangeDetectionStrategy.OnPush
})
export class ShippingEstimationComponent implements OnInit, OnDestroy {
  shippingForm: FormGroup;
  private shippingStateSubject = new BehaviorSubject<ShippingState>({
    status: 'idle',
    cheapestOption: null,
    error: null
  });
  shippingState$: Observable<ShippingState> = this.shippingStateSubject.asObservable();
  private destroy$ = new Subject<void>();

  constructor(
    private fb: FormBuilder,
    private shippingService: ShippingService
  ) {
    this.shippingForm = this.fb.group({
      cep: ['', [Validators.required, Validators.pattern(/^\d{5}-\d{3}$/)]],
    });
  }

  ngOnInit(): void {
    this.shippingForm.get('cep')!.valueChanges.pipe(
      startWith(''),
      debounceTime(300),
      distinctUntilChanged(),
      filter(cep => this.shippingForm.valid),
      tap(() => {
        this.shippingStateSubject.next({status: 'loading', cheapestOption: null, error: null});
        this.shippingForm.get('cep')?.disable();
      }),
      switchMap(cep => this.estimateShipping(cep)),
      takeUntil(this.destroy$)
    ).subscribe(
      state => {
        this.shippingStateSubject.next(state);
        this.shippingForm.get('cep')?.enable();
      },
      error => {
        console.error('Error in shipping estimation:', error);
        this.shippingStateSubject.next({status: 'error', cheapestOption: null, error: 'An unexpected error occurred.'});
        this.shippingForm.get('cep')?.enable();
      }
    );
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  private estimateShipping(cep: string): Observable<ShippingState> {
    if (!cep) {
      return new Observable(observer => {
        observer.next({status: 'idle', cheapestOption: null, error: null});
        observer.complete();
      });
    }
    return this.calculateShippingEstimate(cep).pipe(
      map(options => this.processShippingOptions(options)),
      catchError(error => this.handleError(error))
    );
  }

  private calculateShippingEstimate(cep: string): Observable<ShippingEstimate[]> {
    const request = {
      to: cep.replace('-', ''),
      height: '2',
      width: '12.7',
      length: '17',
      weight: '2',
      quantity: 1
    };
    return this.shippingService.calculateShipping(request);
  }

  private processShippingOptions(options: ShippingEstimate[]): ShippingState {
    if (options.length === 0) {
      return {status: 'no-options', cheapestOption: null, error: null};
    }
    const cheapestOption = options.reduce((prev, curr) => prev.price < curr.price ? prev : curr);
    return {status: 'success', cheapestOption, error: null};
  }

  private handleError(error: any): Observable<ShippingState> {
    console.error('Shipping estimation error:', error);
    return new Observable(observer => {
      observer.next({
        status: 'error',
        cheapestOption: null,
        error: 'Error fetching shipping estimates. Please try again.'
      });
      observer.complete();
    });
  }
}
