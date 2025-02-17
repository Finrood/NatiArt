import {inject, Injectable} from '@angular/core';
import {DomSanitizer, SafeUrl} from '@angular/platform-browser';
import heic2any from 'heic2any';

@Injectable({
  providedIn: 'root'
})
export class ImageService {
  private sanitizer = inject(DomSanitizer);

  isValidImageFile(file: File): boolean {
    return file.type.match(/image\/*/) !== null || file.type.toLowerCase() === 'image/heic';
  }

  async convertFile(file: File): Promise<File> {
    if (file.name.toLowerCase().endsWith('.heic')) {
      try {
        const jpegBlob = await heic2any({ blob: file, toType: 'image/jpeg', quality: 0.8 }) as Blob;
        return new File([jpegBlob], file.name.replace(/\.heic$/i, '.jpg'), { type: 'image/jpeg' });
      } catch (error) {
        console.error('Error converting HEIC to JPEG:', error);
        throw error;
      }
    }
    return file;
  }

  readFileAsDataUrl(file: File): Promise<string> {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = e => resolve(e.target?.result as string);
      reader.onerror = e => reject(e);
      reader.readAsDataURL(file);
    });
  }

  async generateImagePreview(file: File): Promise<{ url: SafeUrl; file: File }> {
    const validFile = await this.convertFile(file);
    const dataUrl = await this.readFileAsDataUrl(validFile);
    const safeUrl = this.sanitizer.bypassSecurityTrustResourceUrl(dataUrl);
    return { url: safeUrl, file: validFile };
  }
}
