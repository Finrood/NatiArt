export interface PaymentCreationResponse {
  paymentId: string;
  creationDate: Date;
  customerId: string;
  billingType: string;
  status: string;
  dueDate: Date;
  invoiceUrl: string;
  invoiceNumber: string;
}
