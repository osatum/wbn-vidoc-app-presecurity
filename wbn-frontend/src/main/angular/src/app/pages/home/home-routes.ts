import {Routes} from "@angular/router";
import {HomeLayout} from '../../layout/home-layout/home-layout';
import {Home} from './home';
import {Denied} from '../error/denied/denied';
import {RegistrationPage} from './registration-page/registration-page';
import {RegistrationActivationPage} from './registration-activation-page/registration-activation-page';

export const routes: Routes = [
  {
    path: '',
    component: HomeLayout,
    children: [
      {
        path: '',
        component: Home,
        data: {
          title: 'ICI Journals Master List',
          analytics: {
            enabled: true,
            pageKey: 'public.home',
            routeTemplate: '/',
            publicView: true,
          },
          description:
            'ICI Journals Master List to globalna baza indeksująca czasopisma naukowe.',
        },
      },
      {
        path: 'registration/activation/:token',
        component: RegistrationActivationPage,
        data: {
          title: 'Aktywacja konta',
          description: 'Aktywacja konta uzytkownika.',
        },
      },
      {
        path: 'registration/:id',
        component: RegistrationPage,
        data: {
          title: 'Rejestracja użytkownika',
          description: 'Rejestracja uzytkownika w jednostce naukowej.',
          analytics: {
            enabled: true,
            pageKey: 'public.registration.institution',
            routeTemplate: '/registration/:id',
            publicView: true,
            params: ['id'],
            resource: {
              type: 'institution',
              idParam: 'id',
            },
          },
        },
      },
      {
        path: 'registration',
        component: RegistrationPage,
        data: {
          title: 'Rejestracja uzytkownika',
          description: 'Rejestracja uzytkownika w jednostce naukowej.',
          analytics: {
            enabled: true,
            pageKey: 'public.registration',
            routeTemplate: '/registration',
            publicView: true,
          },
        },
      },
      {
        path: 'denied',
        component: Denied,
        data: {
          title: 'Brak dostępu',
          description: 'Brak dostepu do zasobu',
          analytics: {
            enabled: true,
            pageKey: 'public.denied',
            routeTemplate: '/denied',
            publicView: true,
          },
        },
      },
    ],
  },
];
