import { Profile } from './profile.model';

export interface UserRegistration {
  username: string;
  password: string;
  profile: Profile;
}
