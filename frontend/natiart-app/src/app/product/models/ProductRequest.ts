import {Product} from "./product.model";

export interface ProductRequest {
  file?: Array<Blob>;
  dto?: Array<Product>;
}
