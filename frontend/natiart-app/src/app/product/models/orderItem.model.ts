export interface OrderItemDto {
  id?: string;
  orderId?: string;
  productId: string;
  quantity: number;
  price?: number;
}
