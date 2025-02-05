export interface PasswordRequirements {
  minLength: number;
  requireUpperCase: boolean;
  requireLowerCase: boolean;
  requireNumber: boolean;
}

export const DEFAULT_REQUIREMENTS: PasswordRequirements = {
  minLength: 8,
  requireUpperCase: true,
  requireLowerCase: true,
  requireNumber: true
};

export function checkPasswordRequirements(
  value: string,
  requirements: PasswordRequirements
) {
  return {
    hasLower: requirements.requireLowerCase ? /[a-z]/.test(value) : true,
    hasUpper: requirements.requireUpperCase ? /[A-Z]/.test(value) : true,
    hasNumber: requirements.requireNumber ? /[0-9]/.test(value) : true,
    hasMinLength: value.length >= requirements.minLength
  };
}
