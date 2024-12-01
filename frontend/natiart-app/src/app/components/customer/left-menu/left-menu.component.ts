import {CommonModule} from '@angular/common';
import {Component, HostListener, OnInit} from '@angular/core';
import {Category} from '../../../models/category.model';
import {CategoryService} from '../../../service/category.service';

@Component({
    selector: 'app-left-menu',
    imports: [CommonModule],
    templateUrl: './left-menu.component.html',
    styles: [] // Empty styles array as we're using only Tailwind classes
})
export class LeftMenuComponent implements OnInit {
  categories: Category[] = [];
  isOpen = true;
  isMobile = false;

  constructor(private categoryService: CategoryService) {
  }

  ngOnInit(): void {
    this.loadCategories();
    this.checkScreenSize();
  }

  @HostListener('window:resize', ['$event'])
  onResize() {
    this.checkScreenSize();
  }

  toggleMenu(): void {
    this.isOpen = !this.isOpen;
  }

  getNavClasses(): string {
    const baseClasses = 'h-full transition-all duration-300 ease-in-out overflow-hidden';
    if (this.isOpen && !this.isMobile) {
      return `${baseClasses} w-64`;
    } else if (!this.isOpen && !this.isMobile) {
      return `${baseClasses} w-16`;
    } else if (this.isOpen && this.isMobile) {
      return `${baseClasses} w-full`;
    } else {
      return `${baseClasses} w-16`;
    }
  }

  getToggleButtonClasses(): string {
    const baseClasses = 'transition-transform duration-300';
    const rotateClass = !this.isOpen ? 'rotate-180' : '';
    const sizeClass = this.isMobile ? 'h-8 w-8' : 'h-6 w-6';
    return `${baseClasses} ${rotateClass} ${sizeClass}`;
  }

  private checkScreenSize(): void {
    this.isMobile = window.innerWidth < 768; // md breakpoint
    if (this.isMobile) {
      this.isOpen = true; // Open menu by default on mobile
    }
  }

  private loadCategories(): void {
    this.categoryService.getCategories().subscribe({
      next: (response) => this.categories = response,
      error: (error) => console.error('Error getting categories:', error)
    });
  }
}
