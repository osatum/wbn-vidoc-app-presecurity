import {Routes} from "@angular/router";
import {authGuard} from '../../core/guards/auth.guard';
import {PortalLayout} from '../../layout/portal-layout/portal-layout';
import {AdminDashboard} from './admin-dashboaord/admin-dashboard';
import {institutionGuard} from '../../core/guards/institution-guard';
import {Denied} from '../error/denied/denied';
import { JournalSearchPage } from './journal-list/journal-search-page';
import { JournalDetails } from './journal-details/journal-details';
import { ChangePassword } from '../account/change-password/change-password';
import { ChangeBasicData } from '../account/change-basic-data/change-basic-data';
import { DeleteMyDataRequest } from './delete-my-data-request/delete-my-data-request';
import { journalSearchUrlGuard } from '../../core/guards/journal-search-url.guard';
import { journalDecisionGuard } from '../../core/guards/journal-decision.guard';


export const routes: Routes = [
  {
    path: 'admin',
    children: [
      {
        path: 'dashboard',
        component: AdminDashboard,
        data: {
          analytics: {
            enabled: true,
            pageKey: 'institution.admin.dashboard',
            routeTemplate: '/institution/admin/dashboard',
          },
        },
      },
      {
        path: 'denied',
        component: Denied,
      },
    ],
  },
  {
    path: 'user',
    children: [
      {
        path: 'dashboard',
        pathMatch: 'full',
        redirectTo: 'journals',
      },
      {
        path: 'change-password',
        component: ChangePassword,
        data: {
          title: 'Zmiana hasła',
          description: 'Aktualizacja hasła do konta.',
          returnUrl: '/institution/user/dashboard',
          viewMode: 'institution',
        },
      },
      {
        path: 'profile/basic-data',
        component: ChangeBasicData,
        data: {
          title: 'Aktualizacja danych',
          description: 'Aktualizacja danych konta.',
        },
      },
      {
        path: 'delete-my-data',
        component: DeleteMyDataRequest,
        data: {
          title: 'Usunięcie danych.',
          description: 'Usunięcie danych użytkownika.',
        },
      },
      {
        path: 'journals',
        component: JournalSearchPage,
        canActivate: [journalSearchUrlGuard],
        runGuardsAndResolvers: 'paramsOrQueryParamsChange',
        data: {
          title: 'Wykaz czasopism naukowych',
          description: 'Lista czasopism naukowych z informacjami o ocenie ICV.',
          analytics: {
            enabled: true,
            pageKey: 'institution.journals.list',
            routeTemplate: '/institution/user/journals',
            params: [],
          },
        },
      },
      {
        path: 'journals/:id/decision',
        loadComponent: () => import('./journal-decision/journal-decision').then((module) => module.JournalDecision),
        canActivate: [journalDecisionGuard],
        runGuardsAndResolvers: 'always',
        data: {
          analytics: {
            enabled: true,
            pageKey: 'institution.journals.decision',
            routeTemplate: '/institution/user/journals/:id/decision',
            params: ['id'],
            resource: { type: 'journal', idParam: 'id' },
          },
        },
      },
      {
        path: 'journals/:id',
        component: JournalDetails,
        data: {
          title: 'Szczegóły czasopisma',
          description: 'Szczegółowe dane czasopisma naukowego.',
          analytics: {
            enabled: true,
            pageKey: 'institution.journals.details',
            routeTemplate: '/institution/user/journals/:id',
            params: ['id'],
            resource: {
              type: 'journal',
              idParam: 'id',
            },
          },
        },
      },
      {
        path: 'denied',
        component: Denied,
      },
    ],
  },
];
