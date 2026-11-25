import { Routes } from '@angular/router';
import { PortalLayout } from './layout/portal-layout/portal-layout';
import { AuthLayout } from './layout/auth-layout/auth-layout';
import { SystemLayout } from './layout/system-layout/system-layout';
import { InstitutionLayout } from './layout/institution-layout/institution-layout';
import { systemOperatorGuard } from './core/guards/system-operator-guard';
import { institutionGuard } from './core/guards/institution-guard';
import { GlobalLayoutComponent } from './layout/global-layout/global-layout.component';

export const routes: Routes = [
  {
    path: '',
    component: GlobalLayoutComponent,
    children: [
      {
        path: 'institution',
        component: InstitutionLayout,
        canActivate: [institutionGuard],
        loadChildren: () => import('./pages/institution/institution-routes').then((m) => m.routes),
      },
      {
        path: 'auth',
        component: AuthLayout,
        loadChildren: () => import('./pages/auth/auth-routes').then((m) => m.routes),
      },
      {
        path: '',
        component: PortalLayout,

        loadChildren: () => import('./pages/home/home-routes').then((m) => m.routes),
      },
    ],
  },

  {
    path: 'system',
    component: SystemLayout,
    canActivate: [systemOperatorGuard],
    loadChildren: () => import('./pages/system/system-routes').then((m) => m.routes),
  }

];
