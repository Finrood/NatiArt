import {OrderItemDto} from "./orderItem.model";

export interface OrderDto {
  id?: string;
  firstname: string;
  lastname: string;
  email: string;
  phone?: string;
  country: string;
  state: string;
  city: string;
  neighborhood: string;
  zipCode: string;
  street: string;
  houseNumber?: string;
  complement?: string;
  orderDate?: Date;
  items: OrderItemDto[];
  deliveryAmount?: number;
  totalAmount?: number;
  shippingQuoteId?: string;
  shippingServiceId?: string;
  shippingDestinationPostalCode?: string;
  shippingQuoteExpiresAt?: string;
  status?: string;
  paymentId?: string;
  paidAt?: string;
  processingAt?: string;
  shippedAt?: string;
  deliveredAt?: string;
  cancelledAt?: string;
  trackingCode?: string;
  trackingUrl?: string;
}
