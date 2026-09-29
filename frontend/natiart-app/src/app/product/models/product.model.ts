import {PersonalizationOption} from "./support/personalization-option";

export interface Product {
  id?: string;
  label: string;
  description?: string;
  originalPrice: number;
  markedPrice: number;
  stockQuantity: number;
  categoryId: string;
  categoryLabel?: string | null;
  packageLabel?: string | null;
  packageId?: string;
  hasFixedGoldenBorder?: boolean;
  availablePersonalizations: PersonalizationOption[];
  tags: string[];
  images: string[];
  active?: boolean;
}
