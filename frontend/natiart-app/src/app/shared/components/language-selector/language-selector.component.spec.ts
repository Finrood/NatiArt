import {TestBed} from '@angular/core/testing';
import {DOCUMENT} from '@angular/common';
import {LOCALE_ID} from '@angular/core';
import {LanguageSelectorComponent, languageUrl} from './language-selector.component';

describe('LanguageSelectorComponent', () => {
  afterEach((): void => document.querySelector('form[data-language-test]')?.remove());

  it('preserves route, query and fragment while replacing only the language prefix', (): void => {
    expect(languageUrl('pt-BR', '/en/product/cup', '?view=details', '#image-2'))
      .toBe('/pt-BR/product/cup?view=details#image-2');
    expect(languageUrl('en', '/pt-BR/checkout', '', '')).toBe('/en/checkout');
    expect(languageUrl('pt-BR', '/login', '', '')).toBe('/pt-BR/login');
    expect(languageUrl('en', '//foreign.example/path', '', '')).toBe('/en/foreign.example/path');
  });

  it('renders native links with names, language and the selected state', (): void => {
    TestBed.configureTestingModule({imports: [LanguageSelectorComponent], providers: [{provide: LOCALE_ID, useValue: 'pt-BR'}]});
    const fixture = TestBed.createComponent(LanguageSelectorComponent);
    fixture.detectChanges();
    const links: NodeListOf<HTMLAnchorElement> = fixture.nativeElement.querySelectorAll('a');
    expect(links[0].textContent).toBe('English');
    expect(links[1].textContent).toBe('Português');
    expect(links[1].getAttribute('aria-current')).toBe('true');
    expect(links[1].getAttribute('hreflang')).toBe('pt-BR');
    expect(links[0].getAttribute('href')).toContain('/en/');
  });

  it('keeps edited form values and cancels a language reload when confirmation is declined', (): void => {
    TestBed.configureTestingModule({imports: [LanguageSelectorComponent], providers: [{provide: DOCUMENT, useValue: document}, {provide: LOCALE_ID, useValue: 'en'}]});
    const form: HTMLFormElement = document.createElement('form');
    form.className = 'ng-dirty'; form.dataset['languageTest'] = 'true';
    const input: HTMLInputElement = document.createElement('input'); input.value = 'Unsaved address';
    form.append(input); document.body.append(form);
    spyOn(window, 'confirm').and.returnValue(false);
    const fixture = TestBed.createComponent(LanguageSelectorComponent);
    const event: MouseEvent = new MouseEvent('click', {cancelable: true});
    fixture.componentInstance.beforeSwitch(event, 'pt-BR');
    expect(event.defaultPrevented).toBeTrue();
    expect(input.value).toBe('Unsaved address');
    expect(window.confirm).toHaveBeenCalled();
  });
});
