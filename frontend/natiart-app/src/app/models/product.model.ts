export interface Product {
  id?: string;
  label: string;
  description?: string;
  originalPrice: number;
  markedPrice: number;
  stockQuantity: number;
  categoryId: string;
  productId: string;
  tags?: Set<string>;
  images: string[];
  active?: boolean;
}
