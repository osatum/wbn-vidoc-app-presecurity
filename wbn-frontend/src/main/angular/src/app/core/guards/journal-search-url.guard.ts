import { inject } from '@angular/core';
import { CanActivateFn, RedirectCommand, Router } from '@angular/router';

export const journalSearchUrlGuard: CanActivateFn = (_route, state) => {
  const router = inject(Router);
  const url = router.parseUrl(state.url);
  if (!Object.hasOwn(url.queryParams, 'searchView')) return true;
  delete url.queryParams['searchView'];
  return new RedirectCommand(url, { replaceUrl: true });
};
