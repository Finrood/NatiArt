import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';

import { Category } from '../models/category.model';
import { CategoryService } from './category.service';

describe('CategoryService', () => {
  let service: CategoryService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideHttpClient(), provideHttpClientTesting()]});
    service = TestBed.inject(CategoryService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getCategories issues GET to the collection URL and emits typed categories', () => {
    const categories: Category[] = [{id: 'c1', label: 'Prints'}];
    let result: Category[] | undefined;
    service.getCategories().subscribe((response: Category[]) => result = response);

    const req: TestRequest = httpMock.expectOne((request) => request.method === 'GET');
    expect(req.request.url.endsWith('/categories')).toBeTrue();
    req.flush(categories);

    expect(result).toEqual(categories);
  });

  it('addCategory issues POST to the create sub-path', () => {
    const created: Category = {id: 'c2', label: 'Originals'};
    let result: Category | undefined;
    service.addCategory({label: 'Originals'}).subscribe((response: Category) => result = response);

    const req: TestRequest = httpMock.expectOne((request) => request.method === 'POST');
    expect(req.request.url.endsWith('/categories/create')).toBeTrue();
    req.flush(created);

    expect(result).toEqual(created);
  });

  it('deleteCategory issues DELETE to the item URL', () => {
    let completed: boolean = false;
    service.deleteCategory('c1').subscribe(() => completed = true);

    const req: TestRequest = httpMock.expectOne((request) => request.method === 'DELETE');
    expect(req.request.url.endsWith('/categories/c1')).toBeTrue();
    req.flush(null);

    expect(completed).toBeTrue();
  });

  it('inverseCategoryVisibility issues PATCH to the visibility sub-path', () => {
    const updated: Category = {id: 'c1', label: 'Prints', active: false};
    let result: Category | undefined;
    service.inverseCategoryVisibility('c1').subscribe((response: Category) => result = response);

    const req: TestRequest = httpMock.expectOne((request) => request.method === 'PATCH');
    expect(req.request.url.endsWith('/categories/c1/visibility/inverse')).toBeTrue();
    req.flush(updated);

    expect(result).toEqual(updated);
  });
});
