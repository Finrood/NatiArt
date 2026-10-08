export interface Profile {
  id?: string;
  version?: number;
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
  houseNumber?: string;
  complement?: string;
}
