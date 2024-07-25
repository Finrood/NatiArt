import { Component, OnInit } from '@angular/core';
import { AsyncPipe, NgIf, NgFor } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Observable, of } from 'rxjs';
import { catchError, map, tap, switchMap } from 'rxjs/operators';
import { ShippingEstimate, ShippingService } from '../../../service/shipping.service';
import { CepFormatDirective } from './cep-format-directive.directive';

@Component({
  selector: 'app-shipping-estimation',
  standalone: true,
  imports: [
    AsyncPipe,
    NgIf,
    NgFor,
    ReactiveFormsModule,
    CepFormatDirective
  ],
  templateUrl: './shipping-estimation.component.html',
  styleUrl: './shipping-estimation.component.css'
})
export class ShippingEstimationComponent implements OnInit {
  shippingForm: FormGroup;
  cheapestOption$: Observable<ShippingEstimate | null> = of(null);
  errorMessage: string = '';
  isLoading: boolean = false;

  constructor(
    private fb: FormBuilder,
    private shippingService: ShippingService
  ) {
    this.shippingForm = this.fb.group({
      cep: ['', [Validators.required, Validators.pattern(/^\d{5}-\d{3}$/)]],
    });
  }

  ngOnInit() {}

  estimateShipping() {
    if (this.shippingForm.valid) {
      const cep = this.shippingForm.get('cep')!.value.replace('-', '');
      this.isLoading = true;
      this.errorMessage = '';

      this.calculateShippingEstimate(cep).pipe(
        map(options => this.getCheapestOption(options)),
        tap(cheapestOption => {
          this.cheapestOption$ = of(cheapestOption);
          this.isLoading = false;
        }),        catchError(error => {
          this.errorMessage = 'Error fetching shipping estimates. Please try again.';
          this.isLoading = false;
          return of(null);
        })
      ).subscribe();
    }
  }

  private calculateShippingEstimate(cep: string): Observable<ShippingEstimate[]> {
    const request = {
      to: cep,
      height: '2',
      width: '12.7',
      length: '17',
      weight: '2',
      quantity: 1
    };

    return this.shippingService.calculateShipping(request);
  }

  private getCheapestOption(options: ShippingEstimate[]): ShippingEstimate | null {
    return options.length > 0 ? options.reduce((prev, curr) => prev.price < curr.price ? prev : curr) : null;
  }
}
