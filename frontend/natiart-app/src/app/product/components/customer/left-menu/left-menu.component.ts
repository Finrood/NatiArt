
import {Component, OnInit} from '@angular/core';
import {Category} from '../../../models/category.model';
import {CategoryService} from '../../../service/category.service';
import {NgClass} from "@angular/common";

@Component({
    selector: 'app-left-menu',
    imports: [NgClass],
    templateUrl: './left-menu.component.html',
    styles: [] // Empty styles array as we're using only Tailwind classes
})
export class LeftMenuComponent implements OnInit {
  categories: Category[] = [];
  isOpen = true;

  constructor(private categoryService: CategoryService) {
  }

  ngOnInit(): void {
    this.loadCategories();
  }

  toggleMenu(): void {
    this.isOpen = !this.isOpen;
  }

  private loadCategories(): void {
    this.categoryService.getCategories().subscribe({
      next: (response) => this.categories = response,
      error: (error) => console.error('Error getting categories:', error)
    });
  }
}
