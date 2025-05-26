// START OF FILE: src/app/models/CartItem.model.ts
import {Product} from "./product.model";

export interface CartItem {
  cartItemId: string; // Unique ID for this specific cart entry
  product: Product;
  goldBorder?: boolean;
  image?: File; // The custom image File object
  quantity: number;
}
