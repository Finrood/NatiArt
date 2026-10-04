export interface PasswordRequirements {
  minLength: number;
  maxUtf8Bytes: number;
  requireUpperCase: boolean;
  requireLowerCase: boolean;
  requireNumber: boolean;
}

export const DEFAULT_REQUIREMENTS: PasswordRequirements = {
  minLength: 8,
  maxUtf8Bytes: 72,
  requireUpperCase: true,
  requireLowerCase: true,
  requireNumber: true
};

export function checkPasswordRequirements(
  value: string | null | undefined,
  requirements: PasswordRequirements
): {hasLower: boolean; hasUpper: boolean; hasNumber: boolean; hasMinLength: boolean; hasMaxBytes: boolean} {
  const password: string = typeof value === 'string' ? value : '';
  return {
    hasLower: requirements.requireLowerCase ? /[a-z]/.test(password) : true,
    hasUpper: requirements.requireUpperCase ? /[A-Z]/.test(password) : true,
    hasNumber: requirements.requireNumber ? /[0-9]/.test(password) : true,
    hasMinLength: password.length >= requirements.minLength,
    hasMaxBytes: new TextEncoder().encode(password).length <= requirements.maxUtf8Bytes
  };
}
