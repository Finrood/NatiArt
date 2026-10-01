import {PersonalizationOption} from './support/personalization-option';

export interface PersonalizationDto {
  id?: string;
  personalizationOptions: Partial<Record<PersonalizationOption, string>>;
}

export interface OrderItemDto {
  id?: string;
  orderId?: string;
  productId: string;
  quantity: number;
  price?: number;
  personalization?: PersonalizationDto;
}
