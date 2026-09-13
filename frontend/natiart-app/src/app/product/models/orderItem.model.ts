export interface OrderItemDto {
  id?: string;
  orderId?: string;
  productId: string;
  productLabel?: string;
  productSku?: string;
  quantity: number;
  price?: number;
  personalizationDto?: {
    personalizationOptions?: Record<string, string>;
  };
}
