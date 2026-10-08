import {Component, inject} from '@angular/core';
import {toSignal} from '@angular/core/rxjs-interop';
import {ActivatedRoute} from '@angular/router';
import {RouterLink} from '@angular/router';

@Component({
  selector: 'app-info-page',
  imports: [RouterLink],
  template: `
    <main class="info-page art-page">
      <div class="info-page-content">
      <p class="mb-3 text-sm uppercase tracking-widest text-primary" i18n>NATIART</p>
      <h1 data-page-heading class="mb-6 art-title">{{ $data()['title'] }}</h1>
      <p class="mx-auto mb-8 max-w-2xl text-secondary-light">{{ $data()['message'] }}</p>
      <a routerLink="/products" class="art-action" i18n>Return to the store</a>
      </div>
    </main>
  `,
  styles: [`
    .info-page { display: flex; align-items: center; min-height: 65svh; }
    .info-page-content { width: min(100%, 42rem); margin-inline: auto; padding-block: 3rem; }
    .info-page h1 { letter-spacing: -.035em; }
    .info-page p { margin-inline: 0; }
  `]
})
export class InfoPageComponent {
  private readonly _route = inject(ActivatedRoute);
  readonly $data = toSignal(this._route.data, {initialValue: this._route.snapshot.data});
}
