import {Product} from "./product.model";

export interface CartItem {
  product: Product;
  goldBorder?: boolean,
  image?: File
  quantity: number;
}
