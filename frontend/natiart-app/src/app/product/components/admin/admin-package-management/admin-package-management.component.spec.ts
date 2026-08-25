import {ComponentFixture, TestBed} from '@angular/core/testing';

import {PackageManagementComponent} from './admin-package-management.component';

describe('PackageManagementComponent', () => {
  let component: PackageManagementComponent;
  let fixture: ComponentFixture<PackageManagementComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [PackageManagementComponent]
    })
      .compileComponents();

    fixture = TestBed.createComponent(PackageManagementComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
