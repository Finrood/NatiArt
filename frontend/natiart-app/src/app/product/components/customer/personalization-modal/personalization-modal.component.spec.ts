import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import * as axe from 'axe-core';

import { PersonalizationModalComponent } from './personalization-modal.component';
import { Product } from '../../../models/product.model';
import { PersonalizationOption } from '../../../models/support/personalization-option';

describe('PersonalizationModalComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PersonalizationModalComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(PersonalizationModalComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('returns false from both getters when the options array is missing (AA2)', () => {
    const fixture = TestBed.createComponent(PersonalizationModalComponent);
    const component: PersonalizationModalComponent = fixture.componentInstance;
    component.product = {
      id: 'p-1',
      label: 'Bare',
      originalPrice: 10,
      markedPrice: 8,
      stockQuantity: 3,
      categoryId: 'cat-1',
      availablePersonalizations: undefined as unknown as PersonalizationOption[],
      tags: [],
      images: [],
    };
    expect((): boolean => component.canAddGoldBorder).not.toThrow();
    expect((): boolean => component.canAddCustomImage).not.toThrow();
    expect(component.canAddGoldBorder).toBeFalse();
    expect(component.canAddCustomImage).toBeFalse();
    expect(component.isValid()).toBeTrue();
  });

  it('reflects offered options and requires a custom image when offered (AA2)', () => {
    const fixture = TestBed.createComponent(PersonalizationModalComponent);
    const component: PersonalizationModalComponent = fixture.componentInstance;
    component.product = {
      id: 'p-2',
      label: 'Fancy',
      originalPrice: 20,
      markedPrice: 18,
      stockQuantity: 5,
      categoryId: 'cat-1',
      availablePersonalizations: [PersonalizationOption.GOLDEN_BORDER, PersonalizationOption.CUSTOM_IMAGE],
      tags: [],
      images: [],
    };
    expect(component.canAddGoldBorder).toBeTrue();
    expect(component.canAddCustomImage).toBeTrue();
    expect(component.isValid()).toBeFalse();
    component.customImage = new File(['x'], 'custom.png', { type: 'image/png' });
    expect(component.isValid()).toBeTrue();
  });

  function selectFile(component: PersonalizationModalComponent, file: File): void {
    const event: Event = new Event('change');
    Object.defineProperty(event, 'target', {value: {files: [file], value: ''}});
    component.onFileSelected(event);
  }

  const artworkProduct: Product = {id: 'art', label: 'A personalized piece', originalPrice: 20,
    markedPrice: 20, stockQuantity: 5, categoryId: 'c', images: [], tags: [],
    availablePersonalizations: [PersonalizationOption.CUSTOM_IMAGE, PersonalizationOption.GOLDEN_BORDER]};
  it('audits required artwork and makes the full gold-option row activate its checkbox', async (): Promise<void> => {
    const fixture = TestBed.createComponent(PersonalizationModalComponent);
    fixture.componentRef.setInput('product', artworkProduct); fixture.componentRef.setInput('show', true);
    fixture.detectChanges(); await fixture.whenStable();
    const row: HTMLLabelElement = fixture.nativeElement.querySelector('.gold-option');
    expect(row.getBoundingClientRect().height).toBeGreaterThanOrEqual(44);
    row.querySelector('span')!.click(); fixture.detectChanges(); await fixture.whenStable();
    expect(fixture.componentInstance.goldBorder).toBeTrue();
    const result: axe.AxeResults = await axe.run(fixture.nativeElement.querySelector('dialog'), {runOnly: {
      type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21aa', 'wcag22aa', 'best-practice']}});
    expect(result.violations.map((issue: axe.Result): string => issue.id + ' ' + issue.nodes.map((node: axe.NodeResult): string => node.failureSummary ?? '').join('\n'))).toEqual([]);
    fixture.destroy();
  });

  it('explains required artwork without a premature error and connects invalid-file feedback to the chooser', () => {
    const fixture = TestBed.createComponent(PersonalizationModalComponent);
    fixture.componentRef.setInput('product', artworkProduct);
    fixture.componentRef.setInput('show', true);
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    const chooser: HTMLInputElement = root.querySelector('#customImage')!;
    expect(chooser.required).toBeTrue();
    expect(chooser.getAttribute('aria-describedby')).toContain('artwork-guidance');
    expect(root.querySelector('[role="alert"]')).toBeNull();
    fixture.componentInstance.goldBorder = true;
    selectFile(fixture.componentInstance, new File(['bad'], 'document.txt', {type: 'text/plain'}));
    fixture.detectChanges();
    expect(chooser.getAttribute('aria-invalid')).toBe('true');
    expect(chooser.getAttribute('aria-describedby')).toContain('artwork-error');
    expect(root.querySelector('#artwork-error')?.textContent).toContain('5 MB');
    expect(fixture.componentInstance.goldBorder).toBeTrue();
    expect(fixture.componentInstance.isValid()).toBeFalse();
    fixture.destroy();
  });

  it('replaces and releases artwork previews while retaining the file through a failed cart request', () => {
    const create: jasmine.Spy = spyOn(URL, 'createObjectURL').and.returnValues('blob:first', 'blob:second', 'blob:third');
    const revoke: jasmine.Spy = spyOn(URL, 'revokeObjectURL');
    const fixture = TestBed.createComponent(PersonalizationModalComponent);
    const component: PersonalizationModalComponent = fixture.componentInstance;
    fixture.componentRef.setInput('product', artworkProduct);
    fixture.componentRef.setInput('closeOnSubmit', false);
    fixture.detectChanges();
    const first: File = new File(['first'], 'first.png', {type: 'image/png'});
    const second: File = new File(['second'], 'second.png', {type: 'image/png'});
    selectFile(component, first);
    expect(component.artwork.urls()['artwork']).toBe('blob:first');
    selectFile(component, second);
    expect(revoke).toHaveBeenCalledWith('blob:first');
    fixture.componentRef.setInput('errorMessage', 'Retry this piece.');
    fixture.detectChanges();
    const submit: jasmine.Spy = jasmine.createSpy('personalize');
    component.personalize.subscribe(submit);
    component.onSubmit();
    expect(submit).toHaveBeenCalledWith({goldBorder: false, customImage: second});
    expect(component.customImage).toBe(second);
    expect(component.artwork.urls()['artwork']).toBe('blob:second');
    component.onCancel();
    expect(revoke).toHaveBeenCalledWith('blob:second');
    expect(component.artwork.urls()).toEqual({});
    selectFile(component, first);
    fixture.destroy();
    expect(revoke).toHaveBeenCalledWith('blob:third');
    expect(create).toHaveBeenCalledTimes(3);
    expect(revoke).toHaveBeenCalledTimes(3);
  });

  it('retains a valid upload if the browser cannot decode its preview', () => {
    spyOn(URL, 'createObjectURL').and.returnValue('blob:unsupported-preview');
    const revoke: jasmine.Spy = spyOn(URL, 'revokeObjectURL');
    const fixture = TestBed.createComponent(PersonalizationModalComponent);
    const component: PersonalizationModalComponent = fixture.componentInstance;
    fixture.componentRef.setInput('product', artworkProduct);
    fixture.componentRef.setInput('show', true);
    fixture.detectChanges();
    const file: File = new File(['image'], 'image.heic', {type: 'image/heic'});
    selectFile(component, file);
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.artwork-preview img').dispatchEvent(new Event('error'));
    fixture.detectChanges();
    expect(component.customImage).toBe(file);
    expect(component.isValid()).toBeTrue();
    expect(fixture.nativeElement.textContent).toContain('Preview unavailable. Your image is still selected.');
    expect(revoke).toHaveBeenCalledWith('blob:unsupported-preview');
    fixture.destroy();
    expect(revoke).toHaveBeenCalledTimes(1);
  });
});
