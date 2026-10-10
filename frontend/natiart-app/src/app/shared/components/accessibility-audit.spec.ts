import {ComponentFixture, TestBed} from '@angular/core/testing';
import {provideHttpClient} from '@angular/common/http';
import {HttpTestingController, provideHttpClientTesting} from '@angular/common/http/testing';
import {provideRouter, Router} from '@angular/router';
import {Observable, of} from 'rxjs';
import {By} from '@angular/platform-browser';
import * as axe from 'axe-core';
import {AppComponent} from '../../app.component';
import {AuthenticationService} from '../../directory/service/authentication.service';
import {TokenService} from '../../directory/service/token.service';
import {LoginComponent} from '../../directory/components/auth/login/login.component';
import {SignupComponent} from '../../directory/components/auth/signup/signup.component';
import {ClaimOrdersComponent} from '../../directory/components/auth/claim-orders/claim-orders.component';
import {PasswordResetRequestComponent} from '../../directory/components/auth/password-reset-request/password-reset-request.component';
import {PasswordResetComponent} from '../../directory/components/auth/password-reset/password-reset.component';
import {CartComponent} from '../../product/components/customer/cart/cart.component';
import {CheckoutComponent} from '../../product/components/customer/checkout/checkout.component';
import {SavedCollectionComponent} from '../../product/components/customer/saved-collection/saved-collection.component';
import {ShopHelpComponent} from './shop-help.component';
import {CartService} from '../../product/service/cart.service';
import {environment} from '../../../environments/environment';
import {CustomerProfileComponent} from '../../product/components/customer/customer-profile/customer-profile.component';
import {AccountDetailsComponent} from '../../directory/components/account/account-details.component';
import {ChangePasswordComponent} from '../../directory/components/account/change-password.component';
import {AdminDashboardComponent} from '../../product/components/admin/admin-dashboard/admin-dashboard.component';
import {TopMenuComponent} from '../../product/components/customer/top-menu/top-menu.component';
import {RoleName, User} from '../../directory/models/user.model';

const account: User = {id: 'audit', username: 'audit@example.test', role: RoleName.ADMIN, externalId: '', profile: {
  id: 'profile', version: 1, firstname: 'Ana', lastname: 'Silva', cpf: '12345678909', phone: '',
  country: 'Brazil', state: 'SP', city: 'São Paulo', neighborhood: 'Centro', zipCode: '01001000',
  street: 'Rua Principal', houseNumber: '123', complement: ''
}};

// Real routed controls, including errors and modal states. Automated rules
// complement keyboard/reflow checks; they do not certify WCAG conformance.
describe('Rendered public journeys accessibility', (): void => {
  let fixture: ComponentFixture<AppComponent>;
  let http: HttpTestingController;
  beforeEach(async (): Promise<void> => {
    localStorage.removeItem('natiart-cart'); localStorage.removeItem('natiart-saved-collection-v1');
    sessionStorage.removeItem('natiart-comparison-v1');
    await TestBed.configureTestingModule({imports: [AppComponent], providers: [
      provideHttpClient(), provideHttpClientTesting(),
      {provide: AuthenticationService, useValue: {isLoggedIn$: of(false), currentUser$: of(null),
        fetchCurrentUser: (): Observable<User> => of(account), resetInactivityTimer: (): void => undefined}},
      {provide: TokenService, useValue: {accessToken: null}},
      provideRouter([
        {path: 'login', component: LoginComponent}, {path: 'register', component: SignupComponent},
        {path: 'cart', component: CartComponent}, {path: 'checkout', component: CheckoutComponent},
        {path: 'claim-orders', component: ClaimOrdersComponent}, {path: 'forgot-password', component: PasswordResetRequestComponent},
        {path: 'reset-password', component: PasswordResetComponent}, {path: 'collection', component: SavedCollectionComponent},
        {path: 'help', component: ShopHelpComponent}, {path: 'admin', component: AdminDashboardComponent},
        {path: 'account', component: CustomerProfileComponent, children: [
          {path: 'profile', component: AccountDetailsComponent}, {path: 'security', component: ChangePasswordComponent}
        ]}
      ])
    ]}).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AppComponent); fixture.detectChanges();
  });
  afterEach((): void => {
    fixture.destroy(); http.verify();
    localStorage.removeItem('natiart-cart'); localStorage.removeItem('natiart-saved-collection-v1');
    sessionStorage.removeItem('natiart-comparison-v1');
  });
  async function visit(path: string): Promise<void> {
    await TestBed.inject(Router).navigateByUrl(path);
    fixture.detectChanges(); await fixture.whenStable(); fixture.detectChanges();
  }
  async function audit(context: HTMLElement = fixture.nativeElement): Promise<void> {
    const result: axe.AxeResults = await axe.run(context, {runOnly: {
      type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa', 'best-practice']
    }});
    expect(result.violations.map((violation: axe.Result): string => violation.id + ': ' +
      violation.nodes.map((node: axe.NodeResult): string => node.target.join(' ') + ' ' + node.failureSummary).join('\n')))
      .withContext(TestBed.inject(Router).url).toEqual([]);
  }
  for (const path of ['/login', '/register', '/cart', '/claim-orders', '/forgot-password', '/reset-password', '/collection', '/help']) {
    it('has one main landmark and no automated violations on ' + path, async (): Promise<void> => {
      await visit(path);
      expect(fixture.nativeElement.querySelectorAll('main').length).toBe(1);
      expect(fixture.nativeElement.querySelector('main h1')).not.toBeNull();
      await audit();
    });
  }
  it('associates guest-order email errors and focuses the field without sending an invalid request', async (): Promise<void> => {
    await visit('/claim-orders');
    const input: HTMLInputElement = fixture.nativeElement.querySelector('input[type="email"]');
    input.value = 'invalid'; input.dispatchEvent(new Event('input', {bubbles: true}));
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', {bubbles: true, cancelable: true}));
    fixture.detectChanges(); await fixture.whenStable();
    expect(input.getAttribute('aria-invalid')).toBe('true'); expect(document.activeElement).toBe(input);
    expect(document.getElementById(input.getAttribute('aria-describedby')!)?.textContent).toContain('valid email');
    http.expectNone(environment.api.directory.url + '/checkout-claim/request'); await audit();
  });
  it('audits sign-in and registration field errors', async (): Promise<void> => {
    await visit('/login');
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', {bubbles: true, cancelable: true}));
    fixture.detectChanges(); await fixture.whenStable();
    expect(fixture.nativeElement.querySelectorAll('[aria-invalid="true"]').length).toBe(2); await audit();
    await visit('/register');
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', {bubbles: true, cancelable: true}));
    fixture.detectChanges(); await fixture.whenStable(); await audit();
  });
  it('audits a populated cart, its drawer and cancel-first removal dialog', async (): Promise<void> => {
    TestBed.inject(CartService).addToCart({id: 'piece', label: 'Handmade botanical dinner plate', originalPrice: 120,
      markedPrice: 120, stockQuantity: 8, categoryId: 'plates', images: [], availablePersonalizations: [], tags: []}, 2).subscribe();
    await visit('/cart'); await audit();
    fixture.nativeElement.querySelector('button[aria-label="Shopping cart"]').click();
    fixture.detectChanges(); await fixture.whenStable(); await audit(fixture.nativeElement.querySelector('dialog'));
    fixture.nativeElement.querySelector('dialog').dispatchEvent(new Event('cancel', {cancelable: true})); fixture.detectChanges();
    const clear: HTMLButtonElement | undefined = Array.from(fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>)
      .find((button: HTMLButtonElement): boolean => button.textContent?.trim() === 'Clear Shopping Cart');
    expect(clear).toBeDefined(); clear!.click(); fixture.detectChanges(); await fixture.whenStable();
    await audit(fixture.nativeElement.querySelector('dialog'));
  });
  it('audits guest checkout contact fields with associated errors', async (): Promise<void> => {
    await visit('/checkout');
    http.expectOne(environment.api.directory.url + '/guest/session').flush({id: 'guest', customerId: null,
      email: null, profile: null, csrfToken: 'test-only', externalId: null, provisioningStatus: null,
      expiresAt: '2099-01-01T00:00:00Z', remembered: false, attemptJson: null});
    fixture.detectChanges(); await fixture.whenStable();
    expect(fixture.nativeElement.querySelectorAll('main').length).toBe(1); await audit();
    fixture.nativeElement.querySelector('form').dispatchEvent(new Event('submit', {bubbles: true, cancelable: true}));
    fixture.detectChanges(); await fixture.whenStable(); await audit();
  });
  async function checkReflow(): Promise<void> {
    const css: string = Array.from(document.styleSheets).flatMap((sheet: CSSStyleSheet): string[] =>
      Array.from(sheet.cssRules).map((rule: CSSRule): string => rule.cssText)).join('\n');
    for (const [width, height] of [[320, 256], [390, 844], [768, 1024], [1280, 800]]) {
      for (const fontSize of [16, 32]) {
        const frame: HTMLIFrameElement = document.createElement('iframe');
        frame.style.cssText = `width:${width}px;height:${height}px;border:0`;
        document.body.appendChild(frame);
        try {
          const doc: Document = frame.contentDocument!;
          doc.open(); doc.write('<!doctype html><html lang="en"><head><style>' + css +
            `\nhtml{font-size:${fontSize}px}*{line-height:1.5!important;letter-spacing:.12em!important;word-spacing:.16em!important}p{margin-bottom:2em!important}` +
            '</style></head><body>' + fixture.nativeElement.innerHTML + '</body></html>'); doc.close();
          await doc.fonts.ready;
          const win: Window = frame.contentWindow!;
          const overflow: string = Array.from(doc.querySelectorAll<HTMLElement>('body *'))
            .filter((element: HTMLElement): boolean => element.getBoundingClientRect().right > width + 1)
            .slice(0, 8).map((element: HTMLElement): string => element.tagName + '.' + element.className).join(', ');
          expect(doc.documentElement.scrollWidth).withContext(`${TestBed.inject(Router).url} ${width}x${height}, ${fontSize}px text: ${overflow}`).toBeLessThanOrEqual(width);
          const row: HTMLElement = doc.querySelector('.store-header-row')!;
          for (const control of row.querySelectorAll<HTMLElement>('a,button')) {
            const rect: DOMRect = control.getBoundingClientRect();
            if (rect.width === 0) continue;
            expect(rect.right).withContext(control.getAttribute('aria-label') ?? control.textContent ?? '').toBeLessThanOrEqual(width);
            expect(rect.bottom).toBeLessThanOrEqual(row.getBoundingClientRect().bottom);
          }
          for (const input of doc.querySelectorAll<HTMLInputElement>('main input:not([type="checkbox"])')) {
            expect(input.getBoundingClientRect().right).toBeLessThanOrEqual(width);
            expect(parseFloat(win.getComputedStyle(input).fontSize)).toBe(fontSize);
          }
          if (height <= 520) expect(win.getComputedStyle(doc.querySelector('app-top-menu')!).position).toBe('static');
        } finally { frame.remove(); }
      }
    }
  }
  it('reflows sign-in and registration steps at 200% text size and WCAG text spacing', async (): Promise<void> => {
    await visit('/login'); await checkReflow();
    await visit('/register'); await checkReflow();
    fixture.debugElement.query(By.directive(SignupComponent)).componentInstance.currentStep = 2;
    fixture.detectChanges(); await fixture.whenStable(); await audit(); await checkReflow();
  });
  it('reflows populated cart and both guest-checkout form steps with enlarged, spaced text', async (): Promise<void> => {
    TestBed.inject(CartService).addToCart({id: 'piece', label: 'Handmade botanical dinner plate', originalPrice: 120,
      markedPrice: 120, stockQuantity: 8, categoryId: 'plates', images: [], availablePersonalizations: [], tags: []}, 2).subscribe();
    await visit('/cart'); await checkReflow();
    await visit('/checkout');
    http.expectOne(environment.api.directory.url + '/guest/session').flush({id: 'guest', customerId: null,
      email: null, profile: null, csrfToken: 'test-only', externalId: null, provisioningStatus: null,
      expiresAt: '2099-01-01T00:00:00Z', remembered: false, attemptJson: null});
    fixture.detectChanges(); await fixture.whenStable(); await checkReflow();
    fixture.debugElement.query(By.directive(CheckoutComponent)).componentInstance.currentStep = 2;
    fixture.detectChanges(); await fixture.whenStable(); await audit(); await checkReflow();
  });
  it('audits and reflows account forms and the administrator navigation with enlarged text', async (): Promise<void> => {
    const menu: TopMenuComponent = fixture.debugElement.query(By.directive(TopMenuComponent)).componentInstance;
    menu.$isLoggedIn.set(true); menu.$isAdmin.set(true);
    for (const path of ['/account/profile', '/account/security', '/admin']) {
      await visit(path);
      expect(fixture.nativeElement.querySelectorAll('main').length).toBe(1);
      await audit(); await checkReflow();
    }
  });
});
