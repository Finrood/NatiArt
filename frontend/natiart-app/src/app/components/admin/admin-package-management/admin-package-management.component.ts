import {Component, OnInit} from '@angular/core';
import {PackageService} from "../../../service/package.service";
import {FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators} from "@angular/forms";
import {BehaviorSubject} from "rxjs";
import {Alert} from "../../../models/alert.model";
import {Package} from "../../../models/package.model";
import {AsyncPipe, NgClass, NgForOf, NgIf} from "@angular/common";

@Component({
  selector: 'app-admin-package-management',
  standalone: true,
  imports: [
    AsyncPipe,
    NgForOf,
    NgIf,
    ReactiveFormsModule,
    NgClass
  ],
  templateUrl: './admin-package-management.component.html',
  styleUrl: './admin-package-management.component.css'
})
export class PackageManagementComponent implements OnInit {
  alert$ = new BehaviorSubject<Alert | null> (null);
  packages = new BehaviorSubject<Package[]>([]);
  isEditingPackage = new BehaviorSubject(false);
  packageForm: FormGroup;

  constructor(private packageService: PackageService, private fb: FormBuilder) {
    this.packageForm = this.fb.group({
      id: [''],
      label: ['', Validators.required],
      height: ['', Validators.compose([Validators.required, Validators.min(0)])],
      width: ['', Validators.compose([Validators.required, Validators.min(0)])],
      depth: ['', Validators.compose([Validators.required, Validators.min(0)])],
      active: [true]
    })
  }

  ngOnInit() {
    this.getPackages();
  }

  private getPackages(): void {
    this.packageService.getPackages().subscribe({
      next: (response) => this.packages.next(response),
      error: (error) => console.error('Error getting packages:', error)
    })
  }

  openModal(pack?: Package): void {
    if (pack) {
      this.isEditingPackage.next(true);
      this.packageForm.setValue({
        id: pack.id || '',
        label: pack.label,
        height: pack.height,
        width: pack.width,
        depth: pack.depth,
        active: pack.active ?? true
      });
    } else {
      this.isEditingPackage.next(false);
      this.packageForm.reset({ active: true });
    }
    const modal = document.getElementById('packageModal');
    modal?.classList.remove('hidden');
  }

  closeModal(): void {
    const modal = document.getElementById('packageModal');
    modal?.classList.add('hidden');
    this.packageForm.reset();
  }

  submitForm(): void {
    if (this.packageForm.valid) {
      if (this.isEditingPackage.value) {
        this.updatePackage();
      } else {
        this.addPackage();
      }
    } else {
      this.validateAllFormFields(this.packageForm);
    }
  }

  private validateAllFormFields(formGroup: FormGroup): void {
    Object.keys(formGroup.controls).forEach(field => {
      const control = formGroup.get(field);
      if (control instanceof FormControl) {
        control.markAsTouched({ onlySelf: true });
      } else if (control instanceof FormGroup) {
        this.validateAllFormFields(control);
      }
    });
  }

  addPackage(): void {
    if (this.packageForm.valid) {
      const pack: Package = this.packageForm.value;
      this.packageService.addPackage(pack).subscribe({
        next: (response) => {
          this.packages.next([...this.packages.value, response]);
          this.closeModal();
        },
        error: (error) => console.error('Error adding package:', error)
      });
    }
  }

  updatePackage(): void {
    if (this.packageForm.valid) {
      const pack: Package = this.packageForm.value;
      this.packageService.updatePackage(pack.id!, pack).subscribe({
        next: (response: Package) => {
          this.packages.next(this.packages.value.map(pak => pak.id === response.id ? response : pak));
          this.closeModal();
        },
        error: (error) => console.error('Error updating package:', error)
      });
    }
  }

  deletePackage(id: string): void {
    this.packageService.deletePackage(id).subscribe({
      next: () => {
        this.packages.next(this.packages.value.filter(pak => pak.id !== id));
        this.showAlert('Package deleted successfully', 'success');
      },
      error: (error: any) => {
        console.error('Error deleting package:', error);
        let errorMessage = 'An error occurred while deleting the package.';
        if (error.status === 400) {
          errorMessage = 'package contains existing products. Delete them before deleting this package';
        } else if (error.status === 404) {
          errorMessage = 'package not found. It may have been already deleted.';
        } else if (error.status === 403) {
          errorMessage = 'You do not have permission to delete this package.';
        }
        this.showAlert(errorMessage, 'error');
      }
    });
  }

  private showAlert(message: string, type: 'success' | 'error'): void {
    this.alert$.next({ message, type });
    setTimeout(() => this.alert$.next(null), 5000);
  }
}
