import {AccessibleDialogComponent} from '../../../../shared/components/accessible-dialog.component';
import {Component, EventEmitter, inject, Input, OnChanges, OnDestroy, Output, SimpleChanges, signal, WritableSignal} from '@angular/core';

import {FormsModule} from '@angular/forms';
import {Product} from '../../../models/product.model';
import {PersonalizationOption} from "../../../models/support/personalization-option";
import {ButtonComponent} from "../../../../shared/components/button.component";
import {reportWarning} from '../../../../shared/service/error-reporting.service';
import {ImageCollection, ImageLoaderService} from '../../../service/image-loader.service';

@Component({
  selector: 'app-personalization-modal',
  imports: [FormsModule, ButtonComponent, AccessibleDialogComponent],
  templateUrl: './personalization-modal.component.html',
  styleUrl: './personalization-modal.component.css'
})
export class PersonalizationModalComponent implements OnChanges, OnDestroy {
  private readonly _imageLoader: ImageLoaderService = inject(ImageLoaderService);
  readonly artwork: ImageCollection = this._imageLoader.create();
  @Input() show: boolean = false;
  @Input() closeOnSubmit: boolean = true;
  @Input() pending: boolean = false;
  @Input() errorMessage: string = '';
  readonly $fileError: WritableSignal<string> = signal('');

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['show'] && !this.show) this.resetForm();
  }
  ngOnDestroy(): void { this.artwork.destroy(); }
  @Input() product: Product | null = null;
  @Output() close = new EventEmitter<void>();
  @Output() personalize = new EventEmitter<{ goldBorder?: boolean, customImage?: File }>();

  goldBorder: boolean = false;
  private readonly $customImage: WritableSignal<File | null> = signal<File | null>(null);
  get customImage(): File | null { return this.$customImage(); }
  set customImage(file: File | null) {
    this.$customImage.set(file);
    this.artwork.update(file ? [{key: 'artwork', source: file}] : []);
  }

  get canAddGoldBorder(): boolean {
    return !!this.product?.availablePersonalizations?.includes(PersonalizationOption.GOLDEN_BORDER);
  }

  get canAddCustomImage(): boolean {
    return !!this.product?.availablePersonalizations?.includes(PersonalizationOption.CUSTOM_IMAGE);
  }

  onFileSelected(event: Event): void {
    this.$fileError.set('');
    const file: File | undefined = (event.target as HTMLInputElement).files?.[0];
    if (file && (!file.type.startsWith('image/') || file.size === 0 || file.size > 5_000_000)) {
      this.customImage = null;
      this.$fileError.set($localize`Choose an image file up to 5 MB.`);
      (event.target as HTMLInputElement).value = '';
    } else if (file) {
      this.customImage = file;
    } else {
      this.customImage = null;
    }
  }

  onCancel(): void {
    this.resetForm();
    this.close.emit();
  }

  onSubmit(): void {
    if (this.pending || !this.isValid()) {
      reportWarning('invalid-input');
      return;
    }
    this.personalize.emit({
      goldBorder: this.canAddGoldBorder ? this.goldBorder : undefined,
      customImage: this.canAddCustomImage ? this.customImage ?? undefined : undefined
    });
    if (this.closeOnSubmit) {
      this.close.emit();
      this.resetForm();
    }
  }

  isValid(): boolean {
    if (this.$fileError()) return false;
    if (this.canAddCustomImage) {
      return !!this.customImage;
    }
    return true;
  }

  private resetForm(): void {
    this.$fileError.set('');
    this.goldBorder = false;
    this.customImage = null;
  }
}
