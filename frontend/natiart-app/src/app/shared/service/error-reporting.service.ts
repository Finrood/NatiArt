import {Injectable} from '@angular/core';
import {environment} from '../../../environments/environment';

export type ErrorSeverity = 'error' | 'warning';

interface SafeErrorReport {
  severity: ErrorSeverity;
  event: string;
  errorType?: string;
  status?: number;
  route?: string;
}

interface RuntimeConfiguration {
  errorReportingUrl?: unknown;
}

const ALLOWED_EVENTS = new Set([
  'application-bootstrap', 'login', 'logout', 'registration', 'token-decoding',
  'authentication', 'cart', 'cart-navigation', 'cart-image', 'image-conversion',
  'shipping-estimation', 'product-loading', 'related-products', 'product-image',
  'category', 'package', 'product-management', 'checkout', 'payment', 'storage',
  'invalid-input', 'unclassified'
]);

const ALLOWED_ERROR_TYPES = new Set(['Error', 'HttpErrorResponse', 'TypeError', 'TimeoutError', 'unknown']);
const SAFE_ROUTE_SEGMENTS = new Set([
  'login', 'logout', 'signup', 'checkout', 'cart', 'products', 'categories', 'packages',
  'product', 'admin', 'pix-payment', 'profile', 'dashboard', 'shipping-estimation'
]);

@Injectable({providedIn: 'root'})
export class ErrorReportingService {
  private static active: ErrorReportingService | null = null;

  constructor() {
    ErrorReportingService.active = this;
  }

  initialize(): void {
    // Resolving this service during bootstrap installs the callback bridge.
  }

  report(eventLabel: string, error?: unknown, severity: ErrorSeverity = 'error'): void {
    const report = this.toSafeReport(eventLabel, error, severity);
    if (!environment.production) {
      const log = severity === 'warning' ? console.warn : console.error;
      log('Client error report', report);
      return;
    }

    const collectorUrl = this.collectorUrl();
    if (!collectorUrl) {
      console.error('Client error reporting is not configured; no report was sent.');
      return;
    }

    void fetch(collectorUrl, {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(report),
      keepalive: true,
    }).catch(() => console.error('Client error report delivery failed.'));
  }

  private collectorUrl(): string | undefined {
    const runtimeValue = (globalThis as typeof globalThis & {__NATIART_CONFIG__?: RuntimeConfiguration})
      .__NATIART_CONFIG__?.errorReportingUrl;
    const configured = typeof runtimeValue === 'string' && runtimeValue.trim().length > 0
      ? runtimeValue.trim()
      : environment.errorReporting?.url;
    return typeof configured === 'string' && configured.trim().length > 0 ? configured.trim() : undefined;
  }

  private toSafeReport(eventLabel: string, error: unknown, severity: ErrorSeverity): SafeErrorReport {
    const report: SafeErrorReport = {
      severity,
      event: ALLOWED_EVENTS.has(eventLabel) ? eventLabel : 'unclassified',
    };
    const errorType = this.errorType(error);
    if (errorType !== 'unknown') {
      report.errorType = errorType;
    }
    const status = this.errorStatus(error);
    if (status !== undefined) {
      report.status = status;
    }
    const route = this.safeRoute();
    if (route) {
      report.route = route;
    }
    return report;
  }

  private errorType(error: unknown): string {
    const candidate = error instanceof Error
      ? error.name
      : typeof error === 'object' && error !== null && typeof (error as {name?: unknown}).name === 'string'
        ? (error as {name: string}).name
        : 'unknown';
    return ALLOWED_ERROR_TYPES.has(candidate) ? candidate : 'unknown';
  }

  private errorStatus(error: unknown): number | undefined {
    const status = typeof error === 'object' && error !== null ? (error as {status?: unknown}).status : undefined;
    return typeof status === 'number' && Number.isInteger(status) && status >= 100 && status <= 599 ? status : undefined;
  }

  private safeRoute(): string | undefined {
    if (typeof location === 'undefined') {
      return undefined;
    }
    const segments = location.pathname.split('/').map(segment => {
      if (!segment) {
        return '';
      }
      return SAFE_ROUTE_SEGMENTS.has(segment) ? segment : ':id';
    });
    return segments.join('/');
  }

  static reportFromCallback(eventLabel: string, error?: unknown, severity: ErrorSeverity = 'error'): void {
    if (ErrorReportingService.active) {
      ErrorReportingService.active.report(eventLabel, error, severity);
    } else {
      const log = severity === 'warning' ? console.warn : console.error;
      log('Client error reporting is not initialized.');
    }
  }
}

export function reportError(eventLabel: string, error?: unknown): void {
  ErrorReportingService.reportFromCallback(eventLabel, error);
}

export function reportWarning(eventLabel: string, error?: unknown): void {
  ErrorReportingService.reportFromCallback(eventLabel, error, 'warning');
}
