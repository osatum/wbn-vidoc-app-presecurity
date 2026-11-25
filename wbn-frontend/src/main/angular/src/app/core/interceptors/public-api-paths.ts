const PUBLIC_API_PATHS = [
  '/api/auth/',
  '/api/home',
  '/api/registration/',
  '/api/password-reset/',
] as const;

export function isPublicApiRequest(url: string): boolean {
  return PUBLIC_API_PATHS.some((path) => url.includes(path));
}
