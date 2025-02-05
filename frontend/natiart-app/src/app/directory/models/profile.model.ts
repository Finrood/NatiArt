export interface Profile {
  id?: string;
  firstname: string;
  lastname: string;
  cpf: string;
  phone?: string;
  country: string;
  state: string;
  city: string;
  neighborhood: string;
  zipCode: string;
  street: string;
  complement?: string;
}
