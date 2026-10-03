import {CurrencyPipe, DatePipe, registerLocaleData} from '@angular/common';
import portuguese from '@angular/common/locales/pt';

describe('Shop locale formatting', () => {
  beforeAll((): void => registerLocaleData(portuguese, 'pt-BR'));

  it('keeps BRL and uses the Portuguese decimal separator', (): void => {
    const price: string | null = new CurrencyPipe('pt-BR').transform(1234.56, 'BRL');
    expect(price?.replace(/\s/g, ' ')).toBe('R$ 1.234,56');
  });

  it('keeps BRL and uses the English decimal separator', (): void => {
    expect(new CurrencyPipe('en').transform(1234.56, 'BRL')).toBe('R$1,234.56');
  });

  it('formats the same calendar date in each language', (): void => {
    expect(new DatePipe('pt-BR').transform('2026-09-30', 'shortDate', 'UTC')).toBe('30/09/2026');
    expect(new DatePipe('en').transform('2026-09-30', 'shortDate', 'UTC')).toBe('9/30/26');
  });
});
