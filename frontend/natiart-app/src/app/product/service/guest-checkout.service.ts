import {inject, Injectable, signal} from '@angular/core';
import {HttpClient, HttpContext, HttpContextToken} from '@angular/common/http';
import {catchError, finalize, Observable, of, shareReplay, tap} from 'rxjs';
import {environment} from '../../../environments/environment';
import {Profile} from '../../directory/models/profile.model';
import {User} from '../../directory/models/user.model';

export const GUEST_REQUEST = new HttpContextToken<boolean>(() => false);
export type CheckoutBuyer = Pick<User, 'id' | 'username' | 'profile' | 'externalId' | 'provisioningStatus'>;
export interface GuestSession {
  id: string; customerId: string | null; email: string | null; profile: Profile | null;
  csrfToken: string; externalId: string | null; provisioningStatus: User['provisioningStatus'];
  expiresAt: string; remembered: boolean; attemptJson: string | null;
}
@Injectable({providedIn: 'root'})
export class GuestCheckoutService {
  readonly $session = signal<GuestSession | null>(null);
  readonly $tracking = signal<boolean>(false);
  readonly $active = signal<boolean>(false);
  private readonly _http: HttpClient = inject(HttpClient);
  private starting: Observable<GuestSession> | null = null;
  private readonly url: string = environment.api.directory.url;
  options(): {withCredentials: boolean; context: HttpContext; headers: Record<string, string>} {
    return {withCredentials: true, context: new HttpContext().set(GUEST_REQUEST, true),
      headers: {'X-Guest-Request': '1', 'X-Guest-CSRF': this.$session()?.csrfToken ?? ''}};
  }
  start(): Observable<GuestSession> {
    this.$active.set(true); this.$tracking.set(false);
    if (!this.starting) {
      this.starting = this._http.post<GuestSession>(this.url + '/guest/session', {}, this.options()).pipe(
        tap((session: GuestSession): void => { this.$session.set(session); }),
        finalize((): void => { this.starting = null; }), shareReplay({bufferSize: 1, refCount: false}));
    }
    return this.starting;
  }
  restore(): Observable<GuestSession | null> {
    return this._http.get<GuestSession>(this.url + '/guest/session', this.options())
      .pipe(tap((session: GuestSession): void => this.$session.set(session)), catchError(() => of(null)));
  }
  details(email: string, profile: Profile, remember: boolean): Observable<GuestSession> {
    this.$active.set(true); this.$tracking.set(false);
    return this._http.post<GuestSession>(this.url + '/guest/session/details', {email, profile, remember}, this.options())
      .pipe(tap((session: GuestSession): void => { this.$session.set(session); }));
  }
  saveAttempt(attemptJson: string | null, expectedOrderId?: string, expectedAttemptKey?: string): Observable<void> {
    return this._http.post<void>(this.url + '/guest/session/attempt', {attemptJson, expectedOrderId, expectedAttemptKey}, this.options())
      .pipe(tap((): void => this.$session.update((session: GuestSession | null): GuestSession | null => session && (!expectedOrderId || JSON.parse(session.attemptJson ?? "{}").currentOrder?.id === expectedOrderId) ? {...session, attemptJson} : session)));
  }
  forget(): Observable<void> {
    return this._http.delete<void>(this.url + '/guest/session', this.options()).pipe(tap((): void => {
      this.$session.set(null); this.$active.set(false);
    }));
  }
  identity(): CheckoutBuyer {
    const session: GuestSession | null = this.$session();
    if (!session?.customerId || !session.profile) throw new Error('Guest details are required');
    return {id: session.customerId, username: 'guest:' + session.id, profile: session.profile,
      externalId: session.externalId ?? '', provisioningStatus: session.provisioningStatus};
  }
  requestClaim(email: string): Observable<void> {
    return this._http.post<void>(this.url + '/checkout-claim/request', {email}, this.options());
  }
}
