import { Category } from './category.model';

export interface Product {
  id?: string;
  label: string;
  description?: string;
  originalPrice: number;
  markedPrice?: number;
  stockQuantity: number;
  categoryId: string;
  tags?: Set<string>;
  images: string[];
}
