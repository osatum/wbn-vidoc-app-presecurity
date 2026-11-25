import { Routes } from '@angular/router';
import { AdminDashoboard } from './admin-dashoboard/admin-dashoboard';
import { Denied } from '../error/denied/denied';
import { InstitutionList } from './institution-list/institution-list';
import { InstitutionDetails } from './institution-details/institution-details';
import { InstitutionCreate } from './institution-create/institution-create';
import { InstitutionEdit } from './institution-edit/institution-edit';
import { InstitutionActivation } from './institution-activation/institution-activation';
import { UserList } from './user-list/user-list';
import { UserDetails } from './user-details/user-details';
import { UserCreate } from './user-create/user-create';
import { UserActivation } from './user-activation/user-activation';
import { UserBasicDataEdit } from './user-basic-data-edit/user-basic-data-edit';
import { UserPasswordEdit } from './user-password-edit/user-password-edit';
import { LoginSession } from './login-session/login-session';
import { ActiveSession } from './active-session/active-session';
import { OvrLayoutComponent } from '../../layout/theme-overwrite/ovr-layout/ovr-layout.component';
import { SystemTools } from './system-tools/system-tools';
import { AccountActivationLog } from './account-activation-log/account-activation-log';
import { PasswordResetRequest } from './password-reset-request/password-reset-request';
import { UserDataDeletionRequest } from './user-data-deletion-request/user-data-deletion-request';
import { EmailMessageLog } from './email-message-log/email-message-log';
import { EmailTemplateList } from './email-template-list/email-template-list';
import { EmailTemplateDetails } from './email-template-details/email-template-details';
import { EmailTemplateEdit } from './email-template-edit/email-template-edit';
import { EmailTypeConfiguration } from './email-type-configuration/email-type-configuration';
import { ChangePassword } from '../account/change-password/change-password';
import { NavigationAuditEventLog } from './navigation-audit-event/navigation-audit-event';

export const routes: Routes = [
  {
    path: 'admin',
    component: OvrLayoutComponent,
    children: [
      {
        path: 'dashboard',
        component: AdminDashoboard,
        data: {
          analytics: {
            enabled: true,
            pageKey: 'system.admin.dashboard',
            routeTemplate: '/system/admin/dashboard',
          },
        },
      },
      {
        path: 'change-password',
        component: ChangePassword,
        data: {
          returnUrl: '/system/admin/dashboard',
          viewMode: 'system',
        },
      },
      {
        path: 'institutions',
        component: InstitutionList,
      },
      {
        path: 'institutions/create',
        component: InstitutionCreate,
      },
      {
        path: 'institutions/:id/edit',
        component: InstitutionEdit,
      },
      {
        path: 'institutions/:id/activation',
        component: InstitutionActivation,
      },
      {
        path: 'institutions/:id/users/create',
        component: UserCreate,
      },
      {
        path: 'institutions/:id',
        component: InstitutionDetails,
      },
      {
        path: 'users',
        component: UserList,
      },
      {
        path: 'users/:id/activation',
        component: UserActivation,
      },
      {
        path: 'users/:id/edit',
        component: UserBasicDataEdit,
      },
      {
        path: 'users/:id/password',
        component: UserPasswordEdit,
      },
      {
        path: 'users/:id',
        component: UserDetails,
      },
      {
        path: 'login-sessions',
        component: LoginSession,
      },
      {
        path: 'account-activations',
        component: AccountActivationLog,
      },
      {
        path: 'password-reset-requests',
        component: PasswordResetRequest,
      },
      {
        path: 'user-data-deletion-requests',
        component: UserDataDeletionRequest,
      },
      {
        path: 'email-messages',
        component: EmailMessageLog,
      },
      {
        path: 'navigation-audit-events',
        component: NavigationAuditEventLog,
      },
      {
        path: 'email-templates',
        component: EmailTemplateList,
      },
      {
        path: 'email-type-configuration',
        component: EmailTypeConfiguration,
      },
      {
        path: 'email-templates/:id/edit',
        component: EmailTemplateEdit,
      },
      {
        path: 'email-templates/:id',
        component: EmailTemplateDetails,
      },
      {
        path: 'active-sessions',
        component: ActiveSession,
      },
      {
        path: 'system-tools',
        component: SystemTools,
      },
      {
        path: 'denied',
        component: Denied,
      },
    ],
  },
];
