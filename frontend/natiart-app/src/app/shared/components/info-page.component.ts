import {Component, inject} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {ActivatedRoute} from '@angular/router';
import {RouterLink} from '@angular/router';

@Component({
  selector: 'app-info-page',
  standalone: true,
  imports: [RouterLink],
  template: `
    <main class="min-h-[60vh] px-6 py-16 text-center">
      <p class="mb-3 text-sm uppercase tracking-widest text-primary">Porcelain Elegance</p>
      <h1 class="mb-6 text-4xl font-serif text-secondary-dark">{{ $data()['title'] }}</h1>
      <p class="mx-auto mb-8 max-w-2xl text-secondary-light">{{ $data()['message'] }}</p>
      <a routerLink="/products" class="font-semibold text-primary underline underline-offset-2">Return to the store</a>
    </main>
  `
})
export class InfoPageComponent {
  private readonly _route = inject(ActivatedRoute);
  readonly $data = toSignal(this._route.data, {initialValue: this._route.snapshot.data});
}
