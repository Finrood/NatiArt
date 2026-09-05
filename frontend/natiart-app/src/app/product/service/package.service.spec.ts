import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';

import { Package } from '../models/package.model';
import { PackageService } from './package.service';

describe('PackageService', () => {
  let service: PackageService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({providers: [provideHttpClient(), provideHttpClientTesting()]});
    service = TestBed.inject(PackageService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('getPackages issues GET to the collection URL and emits typed packages', () => {
    const packages: Package[] = [{id: 'p1', label: 'Tube', height: 10, width: 10, depth: 100}];
    let result: Package[] | undefined;
    service.getPackages().subscribe((response: Package[]) => result = response);

    const req: TestRequest = httpMock.expectOne((request) => request.method === 'GET');
    expect(req.request.url.endsWith('/packages')).toBeTrue();
    req.flush(packages);

    expect(result).toEqual(packages);
  });

  it('addPackage issues POST to the create sub-path', () => {
    const created: Package = {id: 'p2', label: 'Box', height: 5, width: 5, depth: 5};
    let result: Package | undefined;
    service.addPackage({label: 'Box'}).subscribe((response: Package) => result = response);

    const req: TestRequest = httpMock.expectOne((request) => request.method === 'POST');
    expect(req.request.url.endsWith('/packages/create')).toBeTrue();
    req.flush(created);

    expect(result).toEqual(created);
  });

  it('deletePackage issues DELETE to the item URL', () => {
    let completed: boolean = false;
    service.deletePackage('p1').subscribe(() => completed = true);

    const req: TestRequest = httpMock.expectOne((request) => request.method === 'DELETE');
    expect(req.request.url.endsWith('/packages/p1')).toBeTrue();
    req.flush(null);

    expect(completed).toBeTrue();
  });
});
