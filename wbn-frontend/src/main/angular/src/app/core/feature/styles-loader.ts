export const BOOTSTRAP_LAYOUT_STYLE_GROUP = 'bootstrap-layout';

const activeStylesheetsByGroup = new Map<string, string>();
const loadingStylesheets = new WeakMap<HTMLLinkElement, Promise<void>>();

export async function setActiveStylesheet(
  href: string,
  id: string,
  group: string
): Promise<void> {
  const selector = `link[rel="stylesheet"][data-style-group="${group}"]`;
  const previousActiveId = activeStylesheetsByGroup.get(group);
  const stylesheetUrl = new URL(href, document.baseURI).toString();
  activeStylesheetsByGroup.set(group, id);
  markPendingStylesheetsAsCancelled(selector, id);

  const removeInactiveStylesheets = () => {
    if (activeStylesheetsByGroup.get(group) !== id) {
      return;
    }

    document.querySelectorAll<HTMLLinkElement>(selector).forEach(link => {
      if (link.id !== id) {
        removeStylesheet(link);
      }
    });
  };

  const existing = document.getElementById(id) as HTMLLinkElement | null;
  if (existing) {
    await waitForStylesheet(existing);
    removeInactiveStylesheets();
    return;
  }

  const link = document.createElement('link');

  link.id = id;
  link.rel = 'stylesheet';
  link.href = stylesheetUrl;
  link.dataset['styleGroup'] = group;
  link.dataset['requestedBy'] = id;

  await verifyStylesheet(stylesheetUrl);

  const loaded = waitForStylesheet(link);
  document.head.appendChild(link);
  await loaded;
  removeInactiveStylesheets();

  if (previousActiveId && activeStylesheetsByGroup.get(group) !== id) {
    removeStylesheet(link);
  }
}

async function verifyStylesheet(url: string): Promise<void> {
  try {
    const response = await fetch(url, {
      method: 'HEAD',
      cache: 'no-cache',
    });
    const contentType = response.headers.get('content-type') ?? '';

    if (!response.ok) {
      console.error('Stylesheet is not available:', url, response.status, response.statusText);
      return;
    }

    if (!contentType.includes('text/css')) {
      console.error('Stylesheet has invalid content type:', url, contentType);
    }
  } catch (error) {
    console.error('Stylesheet preflight failed:', url, error);
  }
}

function waitForStylesheet(link: HTMLLinkElement): Promise<void> {
  if (link.dataset['loaded'] === 'true') {
    return Promise.resolve();
  }

  if (link.sheet) {
    link.dataset['loaded'] = 'true';
    return Promise.resolve();
  }

  const existing = loadingStylesheets.get(link);
  if (existing) {
    return existing;
  }

  const loaded = new Promise<void>(resolve => {
    link.addEventListener('load', () => {
      link.dataset['loaded'] = 'true';
      resolve();
    }, { once: true });

    link.addEventListener('error', error => {
      const group = link.dataset['styleGroup'] ?? '';
      const isStillActive = activeStylesheetsByGroup.get(group) === link.id;

      if (link.dataset['cancelled'] === 'true' || !isStillActive) {
        resolve();
        return;
      }

      console.error('Failed to load stylesheet:', link.href, error);
      if (activeStylesheetsByGroup.get(group) === link.id) {
        activeStylesheetsByGroup.delete(group);
      }
      removeStylesheet(link);
      resolve();
    }, { once: true });
  });

  loadingStylesheets.set(link, loaded);
  return loaded;
}

function removeStylesheet(link: HTMLLinkElement): void {
  link.dataset['cancelled'] = 'true';
  link.remove();
}

function markPendingStylesheetsAsCancelled(selector: string, activeId: string): void {
  document.querySelectorAll<HTMLLinkElement>(selector).forEach(link => {
    if (link.id !== activeId && link.dataset['loaded'] !== 'true') {
      link.dataset['cancelled'] = 'true';
    }
  });
}
