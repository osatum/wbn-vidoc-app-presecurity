import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import {
  OperatingProfileService,
  canOpenDecisionCard,
} from '../services/operating-profile.service';

export const journalDecisionGuard: CanActivateFn = () => {
  const router = inject(Router);
  const denied = router.createUrlTree(['/institution/user/denied']);
  return inject(OperatingProfileService)
    .currentContext()
    .pipe(
      map((context) => (canOpenDecisionCard(context) ? true : denied)),
      catchError(() => of(denied)),
    );
};
