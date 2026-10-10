import {Component, inject} from '@angular/core';
import {RouterLink} from '@angular/router';
import {StoreContactService} from '../service/store-contact.service';

@Component({selector: 'app-shop-help', imports: [RouterLink], template: `
  <main class="art-page"><div class="art-shell max-w-4xl mx-auto py-12 px-4 sm:py-20">
    <p class="art-eyebrow mb-4" i18n>A little help from the atelier</p>
    <h1 class="art-title mb-5" i18n>Here for your piece.</h1>
    <p class="max-w-2xl text-secondary-light text-lg mb-10" i18n>From your first choice to the moment it arrives, here are the next steps for your order.</p>
    <div class="grid gap-5 sm:grid-cols-2 mb-10">
      <article class="art-panel p-6"><h2 class="font-serif text-2xl mb-3" i18n>Already placed an order?</h2>
        <p class="text-sm text-secondary-light mb-5" i18n>Sign in for your piece’s journey, payment details and carrier reference.</p>
        <a class="art-action inline-flex" routerLink="/account" i18n>View my orders</a>
      </article>
      <article class="art-panel p-6"><h2 class="font-serif text-2xl mb-3" i18n>Checked out as a guest?</h2>
        <p class="text-sm text-secondary-light mb-5" i18n>Verify the email used at checkout to see your orders. Creating an account is optional.</p>
        <a class="art-action inline-flex" routerLink="/claim-orders" i18n>Find my guest orders</a>
      </article>
    </div>
    <section class="art-panel p-6 sm:p-8 mb-8">
      <h2 class="font-serif text-2xl mb-5" i18n>Good to know</h2>
      <div class="divide-y divide-primary-light/40">
        <details class="py-4"><summary class="cursor-pointer font-medium" i18n>When does preparation start?</summary>
          <p class="mt-3 text-sm text-secondary-light" i18n>After the payment provider confirms your PIX. A reserved order or a payment screenshot does not confirm payment. Your order page shows the confirmed status.</p>
        </details>
        <details class="py-4"><summary class="cursor-pointer font-medium" i18n>How do I track my parcel?</summary>
          <p class="mt-3 text-sm text-secondary-light" i18n>The atelier records your carrier reference after dispatch. It appears in your order journey and shipping email. Delivery is recorded by the shop; contact us if your parcel has not arrived.</p>
        </details>
        <details class="py-4"><summary class="cursor-pointer font-medium" i18n>Can I keep guest orders in an account?</summary>
          <p class="mt-3 text-sm text-secondary-light" i18n>Yes. Follow the secure email link and choose to save your orders to an account. If you already have a verified account, use its current password. You can also view orders without creating an account.</p>
        </details>
        <details class="py-4"><summary class="cursor-pointer font-medium" i18n>Who can see my custom artwork?</summary>
          <p class="mt-3 text-sm text-secondary-light" i18n>Your artwork is kept separately from the public gallery. The atelier uses protected order access to prepare your personalized piece.</p>
        </details>
        <details class="py-4"><summary class="cursor-pointer font-medium" i18n>How should I care for porcelain?</summary>
          <p class="mt-3 text-sm text-secondary-light" i18n>Handle porcelain with clean, dry hands and avoid sudden temperature changes.</p>
          <a class="art-link inline-block mt-3" routerLink="/care-instructions" i18n>Read care instructions</a>
        </details>
      </div>
    </section>
    <aside class="border-l-2 border-primary pl-6">
      <h2 class="font-serif text-2xl mb-3" i18n>Let’s talk about your piece.</h2>
      <p class="text-secondary-light mb-3" i18n>Reply to your purchase email with your order reference and what you need help with. Keep passwords and payment credentials private.</p>
      @if (contact.email) { <a class="art-link" [href]="'mailto:' + contact.email">{{ contact.email }}</a> }
    </aside>
  </div></main>`})
export class ShopHelpComponent { readonly contact: StoreContactService = inject(StoreContactService); }
