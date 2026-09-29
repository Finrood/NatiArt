
import {Component, DestroyRef, inject, OnInit, signal} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {Category} from '../../../models/category.model';
import {CategoryService} from '../../../service/category.service';
import {NgClass} from "@angular/common";
import {RouterLink} from '@angular/router';
import {reportError} from '../../../../shared/service/error-reporting.service';

@Component({
    selector: 'app-left-menu',
    imports: [NgClass, RouterLink],
    templateUrl: './left-menu.component.html',
    styles: [] // Empty styles array as we're using only Tailwind classes
})
export class LeftMenuComponent implements OnInit {
  readonly $categories = signal<Category[]>([]);
  get categories(): Category[] { return this.$categories(); }
  set categories(value: Category[]) { this.$categories.set(value); }
  readonly $categoryLoadFailed = signal(false);
  get categoryLoadFailed(): boolean { return this.$categoryLoadFailed(); }
  set categoryLoadFailed(value: boolean) { this.$categoryLoadFailed.set(value); }
  isOpen = true;

  private readonly _categoryService = inject(CategoryService);
  private readonly _destroyed = inject(DestroyRef);

  ngOnInit(): void {
    this.loadCategories();
  }

  toggleMenu(): void {
    this.isOpen = !this.isOpen;
  }

  private loadCategories(): void {
    this._categoryService.getCategories().pipe(takeUntilDestroyed(this._destroyed)).subscribe({
      next: (response) => {
        this.categories = response;
        this.categoryLoadFailed = false;
      },
      error: (error) => {
        reportError('category', error);
        this.categoryLoadFailed = true;
      }
    });
  }
}
