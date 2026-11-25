import { MenuItem } from './menu.model';

export type { MenuItem } from './menu.model';

export const MENU: MenuItem[] = [
  {
    id: 1000,
    label: 'menuitems.menu.text',
    isTitle: true,
  },
  {
    id: 10002,
    label: 'menuitems.dashboard.text',
    icon: 'ri-dashboard-2-line',
    isCollapsed: false,
    link: '/system/admin/dashboard',
  },
  {
    id: 10003,
    label: 'menuitems.institutions.text',
    icon: 'ri-building-2-line',
    isCollapsed: false,
    link: '/system/admin/institutions',
  },
  {
    id: 10003,
    label: 'menuitems.users.text',
    icon: 'ri-group-line',
    isCollapsed: false,
    link: '/system/admin/users',
  },
  {
    id: 10004,
    label: 'menuitems.email-templates.text',
    icon: 'ri-mail-settings-line',
    isCollapsed: false,
    link: '/system/admin/email-templates',
  },
  {
    id: 10005,
    label: 'menuitems.email-type-configuration.text',
    icon: 'ri-mail-check-line',
    isCollapsed: false,
    link: '/system/admin/email-type-configuration',
  },
  {
    id: 2,
    label: 'menuitems.sessions.text',
    isCollapsed: true,
    icon: 'ri-login-circle-line',
    subItems: [
      {
        id: 3,
        label: 'menuitems.sessions.active',
        link: '/system/admin/active-sessions',
        parentId: 2,
      },
      {
        id: 3,
        label: 'menuitems.sessions.login-sessions',
        link: '/system/admin/login-sessions',
        parentId: 2,
      },
      {
        id: 4,
        label: 'menuitems.sessions.account-activations',
        link: '/system/admin/account-activations',
        parentId: 2,
      },
      {
        id: 5,
        label: 'menuitems.sessions.password-reset-requests',
        link: '/system/admin/password-reset-requests',
        parentId: 2,
      },
      {
        id: 6,
        label: 'menuitems.sessions.user-data-deletion-requests',
        link: '/system/admin/user-data-deletion-requests',
        parentId: 2,
      },
      {
        id: 7,
        label: 'menuitems.sessions.email-messages',
        link: '/system/admin/email-messages',
        parentId: 2,
      },
      {
        id: 8,
        label: 'menuitems.sessions.navigation-audit-events',
        link: '/system/admin/navigation-audit-events',
        parentId: 2,
      },
    ],

  },  {
    id: 10003,
    label: 'Narzędzia systemowe',
    icon: 'ri-group-line',
    isCollapsed: false,
    link: '/system/admin/system-tools',
  },

];
