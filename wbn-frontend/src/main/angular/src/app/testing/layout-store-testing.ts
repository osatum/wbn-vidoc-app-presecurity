import { of } from 'rxjs';

const layoutState = {
  LAYOUT: 'vertical',
  LAYOUT_MODE: 'light',
  LAYOUT_WIDTH: 'fluid',
  LAYOUT_POSITION: 'fixed',
  TOPBAR: 'light',
  DATA_PRELOADER: 'disable',
  SIDEBAR_VISIBILITY: 'show',
  SIDEBAR_COLOR: 'dark',
  SIDEBAR_SIZE: 'lg',
  SIDEBAR_IMAGE: 'none',
  SIDEBAR_VIEW: 'default',
};

export function createLayoutStoreMock() {
  return {
    select: () => of(layoutState),
    dispatch: () => undefined,
  };
}
