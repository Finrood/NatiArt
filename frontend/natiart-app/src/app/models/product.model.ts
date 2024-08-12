export interface Product {
  id?: string;
  label: string;
  description?: string;
  originalPrice: number;
  markedPrice: number;
  stockQuantity: number;
  categoryId: string;
  hasGold?: boolean;
  canPersonaliseGold: boolean;
  canPersonaliseImage: boolean;
  tags?: Set<string>;
  images: string[];
  active?: boolean;
}
