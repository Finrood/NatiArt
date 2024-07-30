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
  complement?: string;
  orderDate?: Date;
  items: OrderItemDto[];
  deliveryAmount?: number;
  totalAmount?: number;
  status?: string;
}
