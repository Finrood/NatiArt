import {AfterViewInit, DestroyRef, signal, inject, Component, OnInit, ViewChild} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {PackageService} from '../../../service/package.service';
import {FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {BehaviorSubject} from 'rxjs';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {finalize} from 'rxjs/operators';
import {Package} from '../../../models/package.model';
import { AsyncPipe, NgClass } from '@angular/common';
import {AlertMessageComponent} from "../../../../shared/components/alert-message/alert-message.component";
import {NatiartFormFieldComponent} from "../../../../shared/components/natiart-form-field/natiart-form-field.component";
import {ButtonComponent} from "../../../../shared/components/button.component";
import {reportError} from '../../../../shared/service/error-reporting.service';

@Component({
  selector: 'app-admin-package-management',
  imports: [
    AsyncPipe,
    ReactiveFormsModule,
    NgClass,
    AlertMessageComponent,
    NatiartFormFieldComponent,
    ButtonComponent
],
  templateUrl: './admin-package-management.component.html',
  styleUrls: ['./admin-package-management.component.css']
})
export class PackageManagementComponent implements OnInit, AfterViewInit {
  packages = new BehaviorSubject<Package[]>([]);
  isEditingPackage = new BehaviorSubject(false);
  packageForm: FormGroup;
  readonly $modalVisible = signal(false);
  get modalVisible(): boolean { return this.$modalVisible(); }
  set modalVisible(value: boolean) { this.$modalVisible.set(value); }
  readonly $isSubmitting = signal(false);
  get isSubmitting(): boolean { return this.$isSubmitting(); }
  set isSubmitting(value: boolean) { this.$isSubmitting.set(value); }
  private readonly _destroyed = inject(DestroyRef);
  private formGeneration = 0;
  private pendingAlerts: Array<{message: string; type: 'success' | 'error'}> = [];

  @ViewChild('alertMessages') alertMessagesComponent!: AlertMessageComponent;

  private readonly _packageService: PackageService = inject(PackageService);
  private readonly _fb: FormBuilder = inject(FormBuilder);

  constructor() {
    this.packageForm = this._fb.group({
      id: [''],
      label: ['', Validators.required],
      height: ['', [Validators.required, Validators.min(0)]],
      width: ['', [Validators.required, Validators.min(0)]],
      depth: ['', [Validators.required, Validators.min(0)]],
      active: [true]
    });
  }

  ngOnInit() {
    this.getPackages();
  }

  ngAfterViewInit(): void {
    this.pendingAlerts.splice(0).forEach(alert => this.showAlert(alert.message, alert.type));
  }

  openModal(pack?: Package): void {
    this.formGeneration++;
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
    this.modalVisible = true;
  }

  closeModal(): void {
    this.formGeneration++;
    this.modalVisible = false;
    this.packageForm.reset();
  }

  submitForm(): void {
    if (this.isSubmitting) {
      return;
    }
    if (this.packageForm.valid) {
      this.isSubmitting = true;
      const generation = this.formGeneration;
      if (this.isEditingPackage.value) {
        this.updatePackage(generation);
      } else {
        this.addPackage(generation);
      }
    } else {
      this.validateAllFormFields(this.packageForm);
    }
  }

  addPackage(generation = this.formGeneration): void {
    const pack: Package = this.packageForm.value;
    this._packageService.addPackage(pack).pipe(takeUntilDestroyed(this._destroyed), finalize(() => this.isSubmitting = false)).subscribe({
      next: (response) => {
        this.packages.next([...this.packages.value, response]);
        if (generation === this.formGeneration) {
          this.closeModal();
        }
        this.showAlert($localize`Package created successfully`, 'success');
      },
      error: (error) => {
        reportError('package', error);
        this.showAlert(this.writeErrorMessage(error), 'error');
      }
    });
  }

  updatePackage(generation = this.formGeneration): void {
    const pack: Package = this.packageForm.value;
    this._packageService.updatePackage(pack.id!, pack).pipe(takeUntilDestroyed(this._destroyed), finalize(() => this.isSubmitting = false)).subscribe({
      next: (response: Package) => {
        this.packages.next(
          this.packages.value.map(p => p.id === response.id ? response : p)
        );
        if (generation === this.formGeneration) {
          this.closeModal();
        }
        this.showAlert($localize`Package updated successfully`, 'success');
      },
      error: (error) => {
        reportError('package', error);
        this.showAlert(this.writeErrorMessage(error), 'error');
      }
    });
  }

  deletePackage(id: string): void {
    const pack = this.packages.value.find(item => item.id === id);
    if (!pack || !window.confirm($localize`Delete package "${pack.label}:PACKAGE_LABEL:"?`)) {
      return;
    }
    this._packageService.deletePackage(id).subscribe({
      next: () => {
        this.packages.next(this.packages.value.filter(p => p.id !== id));
        this.showAlert($localize`Package deleted successfully`, 'success');
      },
      error: (error: HttpErrorResponse) => {
        reportError('package', error);
        let errorMessage = $localize`An error occurred while deleting the package.`;
        if (error.status === 400) {
          errorMessage = $localize`Package is used by products. Reassign those products before deleting this package.`;
        } else if (error.status === 404) {
          errorMessage = $localize`Package not found. It may have been already deleted.`;
        } else if (error.status === 403) {
          errorMessage = $localize`You do not have permission to delete this package.`;
        }
        this.showAlert(errorMessage, 'error');
      }
    });
  }

  private getPackages(): void {
    this._packageService.getPackages().subscribe({
      next: (response) => this.packages.next(response),
      error: (error) => {
        reportError('package', error);
        this.showAlert($localize`Unable to load packages. Please retry.`, 'error');
      }
    });
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

  private showAlert(message: string, type: 'success' | 'error'): void {
    if (this.alertMessagesComponent) {
      this.alertMessagesComponent.showAlert({ message, type });
    } else {
      this.pendingAlerts.push({message, type});
    }
  }

  private writeErrorMessage(error: HttpErrorResponse): string {
    if (error.status === 409) {
      return $localize`A package with this label already exists.`;
    }
    if (error.status === 0) {
      return $localize`The package service is unavailable. Please retry.`;
    }
    return $localize`Unable to save the package. Your changes are still in the form.`;
  }
}
