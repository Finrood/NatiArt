import { TestBed } from '@angular/core/testing';

import { TokenService } from './token.service';

describe('TokenService', () => {
  let service: TokenService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(TokenService);
    localStorage.clear();
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('round-trips tokens through storage', () => {
    service.accessToken = 'access-123';
    service.refreshToken = 'refresh-123';

    expect(service.accessToken).toBe('access-123');
    expect(service.refreshToken).toBe('refresh-123');
  });

  it('clearTokens removes both tokens without logging', () => {
    const logSpy: jasmine.Spy = spyOn(console, 'log');
    service.accessToken = 'access-123';
    service.refreshToken = 'refresh-123';

    service.clearTokens();

    expect(service.accessToken).toBeNull();
    expect(service.refreshToken).toBeNull();
    expect(logSpy).not.toHaveBeenCalled();
  });

  it('tolerates unavailable storage instead of throwing', () => {
    spyOn(Storage.prototype, 'setItem').and.throwError(new Error('denied'));
    spyOn(Storage.prototype, 'getItem').and.throwError(new Error('denied'));

    expect(() => service.accessToken = 'access-123').not.toThrow();
    expect(service.accessToken).toBeNull();
    expect(() => service.clearTokens()).not.toThrow();
  });

  it('notifies tokens-cleared listeners exactly once per clear', () => {
    const listener: jasmine.Spy = jasmine.createSpy('tokensCleared');
    service.onTokensCleared(listener);
    service.accessToken = 'access-123';
    service.refreshToken = 'refresh-123';

    service.clearTokens();

    expect(listener).toHaveBeenCalledTimes(1);
  });

  it('stops notifying after the returned unsubscribe runs', () => {
    const listener: jasmine.Spy = jasmine.createSpy('tokensCleared');
    const unsubscribe: () => void = service.onTokensCleared(listener);
    unsubscribe();

    service.clearTokens();

    expect(listener).not.toHaveBeenCalled();
  });

  it('still notifies healthy listeners when one listener throws', () => {
    const healthy: jasmine.Spy = jasmine.createSpy('healthy');
    service.onTokensCleared(() => {
      throw new Error('listener boom');
    });
    service.onTokensCleared(healthy);

    expect(() => service.clearTokens()).not.toThrow();
    expect(healthy).toHaveBeenCalledTimes(1);
    expect(service.accessToken).toBeNull();
    expect(service.refreshToken).toBeNull();
  });
});
