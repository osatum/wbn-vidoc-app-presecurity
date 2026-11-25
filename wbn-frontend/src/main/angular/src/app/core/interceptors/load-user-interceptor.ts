import { HttpInterceptorFn } from '@angular/common/http';
import {AuthService} from '../services/auth.service';
import {inject} from '@angular/core';
import {isPublicApiRequest} from './public-api-paths';

let lastLoadedToken: string | null = null;   // token przy którym pobrano userContext

export const loadUserInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.token();

  console.log('loadUserInterceptor', req.url);

  // zawsze przepuszczamy dalej request
  const stream$ = next(req);

  // brak tokena → brak userContext
  if (!token) {
    lastLoadedToken = null;
    return stream$;
  }

  // jeśli żądanie to /auth/me → nie wywołujemy loadUserContext (zapobiega pętli)
  if (req.url.endsWith('/auth/me') || isPublicApiRequest(req.url)) {
    return stream$;
  }

  // jeśli token się nie zmienił → nie pobieramy kontekstu ponownie
  if (token === lastLoadedToken) {
    return stream$;
  }

  // token się zmienił → aktualizujemy zapamiętany token
  lastLoadedToken = token;

  // wywołujemy tylko raz dla zmienionego tokena
  auth.loadUserProfile().subscribe({
    error: () => {
      // jeśli token jest nieaktualny → reset stanu
      lastLoadedToken = null;
      auth.logoutObservabler();
    }
  });

  return stream$;
};
