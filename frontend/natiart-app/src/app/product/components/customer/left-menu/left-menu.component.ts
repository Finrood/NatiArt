
import {Component, computed, DestroyRef, inject, input, OnInit, signal} from '@angular/core';
import {Category} from '../../../models/category.model';
import {CategoryService} from '../../../service/category.service';
import {RouterLink} from '@angular/router';
import {PagedList} from '../../../../shared/service/paged-list';
import {PageControlsComponent} from '../../../../shared/components/page-controls.component';

@Component({
    selector: 'app-left-menu',
    imports: [RouterLink, PageControlsComponent],
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
  readonly $filtersOpen = signal(false);
  readonly $selectedCategoryId = input<string | undefined>(undefined, {alias: 'selectedCategoryId'});
  readonly $selectedCategoryLabel = computed((): string =>
    this.$categories().find(category => category.id === this.$selectedCategoryId())?.label
      || $localize`Selected category`);

  private readonly _categoryService = inject(CategoryService);
  readonly pages = new PagedList<Category>((page: number) => this._categoryService.getCategoriesPage(page),
    (items: Category[]): void => {this.categories = items; this.categoryLoadFailed = false;}, inject(DestroyRef));

  ngOnInit(): void {
    this.loadCategories();
  }

  toggleMenu(): void {
    this.$filtersOpen.update((open: boolean): boolean => !open);
  }

  private loadCategories(): void { this.pages.load(0); }
}
