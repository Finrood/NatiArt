import {ErrorReportingService, reportError, reportWarning} from './error-reporting.service';
import {environment} from '../../../environments/environment';

describe('ErrorReportingService', () => {
  it('emits an allowlisted development report without arbitrary error data', () => {
    const errorSpy = spyOn(console, 'error');
    const service = new ErrorReportingService();

    service.report('Request failed with secret-user@example.test', {
      name: 'Error',
      message: 'password=do-not-send',
      status: 502,
      url: 'https://example.test/products/p-123?token=secret',
      stack: 'secret stack trace',
      token: 'do-not-log',
    });

    const report = errorSpy.calls.mostRecent().args[1] as Record<string, unknown>;
    expect(report).toEqual(jasmine.objectContaining({severity: 'error', event: 'unclassified', status: 502}));
    expect(JSON.stringify(report)).not.toContain('password');
    expect(JSON.stringify(report)).not.toContain('secret');
    expect(JSON.stringify(report)).not.toContain('stack');
    expect(JSON.stringify(report)).not.toContain('p-123');
  });

  it('uses the configured production collector and sends only the safe schema', async () => {
    const service = new ErrorReportingService();
    const fetchSpy = spyOn(window, 'fetch').and.returnValue(Promise.resolve(new Response()));
    environment.production = true;
    environment.errorReporting = {url: '/server/directory/client-errors'};

    try {
      service.report('shipping-estimation', {
        name: 'HttpErrorResponse',
        message: 'secret cart product p-123',
        status: 503,
        url: 'https://example.test/products/p-123?token=secret',
        stack: 'secret stack trace',
      });
      await Promise.resolve();
      const request = fetchSpy.calls.mostRecent().args[1] as RequestInit;
      const body = JSON.parse(String(request.body)) as Record<string, unknown>;
      expect(fetchSpy.calls.mostRecent().args[0]).toBe('/server/directory/client-errors');
      expect(body).toEqual(jasmine.objectContaining({severity: 'error', event: 'shipping-estimation', status: 503}));
      expect(JSON.stringify(body)).not.toContain('secret');
      expect(JSON.stringify(body)).not.toContain('stack');
      expect(JSON.stringify(body)).not.toContain('p-123');
    } finally {
      environment.production = false;
      environment.errorReporting = {url: ''};
    }
  });

  it('routes warnings through the same safe channel', () => {
    const warningSpy = spyOn(console, 'warn');
    new ErrorReportingService();

    reportWarning('invalid-input', 'private value');

    expect(warningSpy).toHaveBeenCalledWith('Client error report', jasmine.objectContaining({severity: 'warning'}));
  });

  it('keeps callback-style reporting on the central service', () => {
    const errorSpy = spyOn(console, 'error');
    new ErrorReportingService();

    reportError('login', new Error('private failure'));

    expect(errorSpy).toHaveBeenCalledTimes(1);
    expect(errorSpy.calls.mostRecent().args[1]).toEqual(jasmine.objectContaining({event: 'login'}));
  });
});
