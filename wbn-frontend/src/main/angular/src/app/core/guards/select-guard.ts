import {CanActivateFn, Router} from '@angular/router';
import {inject} from '@angular/core';
import {AuthService} from '../services/auth.service';

export const selectGuard: CanActivateFn = (route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isPendingInstitutionsSnapshot()) {
    return true;
  }

  router.navigateByUrl('/auth/login');
  return false;

  return true;
};
