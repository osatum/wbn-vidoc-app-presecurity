import {Routes} from "@angular/router";
import {LoginPage} from './login-page/login-page';
import {SelectInstitution} from './select-institution/select-institution';
import {SelectMembership} from './select-membership/select-membership';
import {AuthLayout} from '../../layout/auth-layout/auth-layout';
import {selectGuard} from '../../core/guards/select-guard';
import { PasswordResetRequestPage } from './password-reset-request/password-reset-request';
import { PasswordResetConfirmPage } from './password-reset-confirm/password-reset-confirm';
import { UserDataDeletionConfirmPage } from './user-data-deletion-confirm/user-data-deletion-confirm';


export const routes: Routes = [
  {
    path: 'login',
    component: LoginPage,
    data: {
      analytics: {
        enabled: true,
        pageKey: 'auth.login',
        routeTemplate: '/auth/login',
        publicView: true,
      },
      subtitle: 'Zaloguj się',
      title: 'Dostęp do platformy',
      description:
        'Strona logowania do systemu zapewnia bezpieczny dostęp do platformy dla uprawnionych użytkowników.',
    },
  },
  {
    path: 'password-reset',
    component: PasswordResetRequestPage,
    data: {
      analytics: {
        enabled: true,
        pageKey: 'auth.password-reset',
        routeTemplate: '/auth/password-reset',
        publicView: true,
      },
      subtitle: '',
      title: 'Reset hasła',
      description: 'Formularz zgłoszenia resetu hasła.',
    },
  },
  {
    path: 'password-reset/confirm/:token',
    component: PasswordResetConfirmPage,
    data: {
      subtitle: '',
      title: 'Nowe hasło',
      description: 'Formularz ustawiania nowego hasła.',
    },
  },
  {
    path: 'delete-my-data/confirm/:token',
    component: UserDataDeletionConfirmPage,
    data: {
      subtitle: '',
      title: 'Usuniecie danych',
      description: 'Potwierdzenie usunięcia danych użytkownika.',
    },
  },
  {
    path: 'select-institutions',
    component: SelectInstitution,
    canActivate: [selectGuard],
    data: {
      analytics: {
        enabled: true,
        pageKey: 'auth.select-institutions',
        routeTemplate: '/auth/select-institutions',
        publicView: true,
      },
      subtitle: '',
      title: 'Wybierz instytucję',
      description: 'Strona umożliwia wybór instytucji i roli użytkownika.',
    },
  },
  {
    path: 'select-membership',
    component: SelectMembership,
    canActivate: [selectGuard],
    data: {
      analytics: {
        enabled: true,
        pageKey: 'auth.select-membership',
        routeTemplate: '/auth/select-membership',
        publicView: true,
      },
      subtitle: '',
      title: 'Wybierz rolę',
      description: 'Strona umożliwia wybór roli użytkownika w wybranej instytucji.',
    },
  },
];
