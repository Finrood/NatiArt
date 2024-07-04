import { Category } from './category.model';

export interface Product {
  id?: string;
  label: string;
  description?: string;
  originalPrice: number;
  markedPrice?: number;
  stockQuantity: number;
  category: Category;
  tags: Set<string>;
  images: string[];
}
