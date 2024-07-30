import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AdminPackageManagementComponent } from './admin-package-management.component';

describe('AdminPackageManagementComponent', () => {
  let component: AdminPackageManagementComponent;
  let fixture: ComponentFixture<AdminPackageManagementComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminPackageManagementComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(AdminPackageManagementComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
