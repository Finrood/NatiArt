// START OF FILE: src/app/models/CartItem.model.ts
import {Product} from "./product.model";

export interface CartItem {
  cartItemId: string; // Unique ID for this specific cart entry
  product: Product;
  goldBorder?: boolean;
  image?: File; // The custom image File object
  /** Opaque server-owned id populated when checkout uploads the artwork. */
  customImageUploadId?: string;
  quantity: number;
}
