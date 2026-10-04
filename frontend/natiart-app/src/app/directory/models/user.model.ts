import {Profile} from './profile.model';

export enum RoleName {
  ADMIN = 'ADMIN',
  USER = 'USER'
}

export interface User {
  id: string;
  username: string;
  profile: Profile;
  role: RoleName;
  externalId: string;
  provisioningStatus?: 'PENDING' | 'IN_PROGRESS' | 'SUCCEEDED' | 'FAILED';
  provisioningNextAttemptAt?: string;
}
