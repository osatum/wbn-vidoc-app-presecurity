import { Injectable, signal } from '@angular/core';

export const SESSION_REFRESH_THRESHOLD_SECONDS = 90;


type JwtPayload = {
  exp?: number;
  iat?: number;
};

export type JwtTiming = {
  expiresAt: number;
  issuedAt: number | null;
};

@Injectable({ providedIn: 'root' })
export class SessionActivityService {
  private readonly lastBackendActivityAtSignal = signal<number | null>(null);

  readonly lastBackendActivityAt = this.lastBackendActivityAtSignal.asReadonly();

  markBackendActivity(): void {
    this.lastBackendActivityAtSignal.set(Date.now());
  }

  clearBackendActivity(): void {
    this.lastBackendActivityAtSignal.set(null);
  }

  readTokenTiming(token: string | undefined | null): JwtTiming | null {
    if (!token) {
      return null;
    }

    const payload = this.decodePayload(token);
    if (payload?.exp === undefined) {
      return null;
    }

    return {
      expiresAt: payload.exp * 1000,
      issuedAt: payload.iat === undefined ? null : payload.iat * 1000,
    };
  }

  secondsUntilTokenExpiration(token: string | undefined | null, now = Date.now()): number | null {
    const timing = this.readTokenTiming(token);
    if (timing === null) {
      return null;
    }

    return Math.ceil((timing.expiresAt - now) / 1000);
  }

  private decodePayload(token: string): JwtPayload | null {
    const payload = token.split('.')[1];
    if (!payload) {
      return null;
    }

    try {
      const base64 = payload.replace(/-/g, '+').replace(/_/g, '/');
      const paddedBase64 = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');

      return JSON.parse(atob(paddedBase64)) as JwtPayload;
    } catch {
      return null;
    }
  }
}
