import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';

import { LeftMenuComponent } from './left-menu.component';
import { CategoryService } from '../../../service/category.service';

describe('LeftMenuComponent', () => {
  let categoryService: jasmine.SpyObj<CategoryService>;

  beforeEach(async () => {
    categoryService = jasmine.createSpyObj<CategoryService>('CategoryService', ['getCategories']);
    categoryService.getCategories.and.returnValue(of([]));
    await TestBed.configureTestingModule({
      imports: [LeftMenuComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        {provide: CategoryService, useValue: categoryService},
      ],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(LeftMenuComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('shows a fallback message when categories cannot be loaded', () => {
    categoryService.getCategories.and.returnValue(throwError(() => new Error('backend down')));
    const fixture = TestBed.createComponent(LeftMenuComponent);

    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Categories are temporarily unavailable.');
  });
});
