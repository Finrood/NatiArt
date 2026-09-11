export interface PaymentCreationRequest {
  paymentProcessor: string; // ASAAS
  customerId: string;
  orderId?: string;
  value: number;
  billingType: string; // PIX or CREDIT_CARD
}
