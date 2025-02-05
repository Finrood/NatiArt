import {PersonalizationOption} from "./support/personalization-option";

export interface Product {
  id?: string;
  label: string;
  description?: string;
  originalPrice: number;
  markedPrice: number;
  stockQuantity: number;
  categoryId: string;
  packageId?: string;
  hasFixedGoldenBorder?: boolean;
  availablePersonalizations: Set<PersonalizationOption>;
  tags: Set<string>;
  images: string[];
  active?: boolean;
}
