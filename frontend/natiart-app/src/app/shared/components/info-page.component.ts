import {Component, inject} from '@angular/core';
import {ActivatedRoute} from '@angular/router';
import {RouterLink} from '@angular/router';

@Component({
  selector: 'app-info-page',
  standalone: true,
  imports: [RouterLink],
  template: `
    <main class="min-h-[60vh] px-6 py-16 text-center">
      <p class="mb-3 text-sm uppercase tracking-widest text-primary">Porcelain Elegance</p>
      <h1 class="mb-6 text-4xl font-serif text-secondary-dark">{{ title }}</h1>
      <p class="mx-auto mb-8 max-w-2xl text-secondary-light">{{ message }}</p>
      <a routerLink="/dashboard" class="font-semibold text-primary underline underline-offset-2">Return to the store</a>
    </main>
  `
})
export class InfoPageComponent {
  private readonly route = inject(ActivatedRoute);
  readonly title = this.route.snapshot.data['title'] as string;
  readonly message = this.route.snapshot.data['message'] as string;
}
