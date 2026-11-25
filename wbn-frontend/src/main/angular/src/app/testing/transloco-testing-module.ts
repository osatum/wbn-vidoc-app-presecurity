import { TranslocoTestingModule } from '@jsverse/transloco';

export const translocoTestingModule = TranslocoTestingModule.forRoot({
  langs: {
    en: {},
    pl: {},
  },
  translocoConfig: {
    availableLangs: ['en', 'pl'],
    defaultLang: 'pl',
    reRenderOnLangChange: true,
    prodMode: true,
  },
  preloadLangs: true,
});
