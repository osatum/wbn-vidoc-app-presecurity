import { downloadFile } from './file-download';

describe('downloadFile', () => {
  const createObjectURL = vi.fn();
  const revokeObjectURL = vi.fn();

  beforeEach(() => {
    vi.resetAllMocks();
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL });
    createObjectURL.mockReturnValue('blob:download-test');
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it('starts the download and releases its anchor and object URL', () => {
    const file = new Blob(['report']);
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      expect(this.download).toBe('report.xls');
      expect(this.href).toBe('blob:download-test');
      expect(this.isConnected).toBe(true);
    });

    expect(downloadFile(document, file, 'report.xls')).toBe(true);
    expect(click).toHaveBeenCalledOnce();
    expect(createObjectURL).toHaveBeenCalledWith(file);
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:download-test');
    expect(document.querySelector('a[download="report.xls"]')).toBeNull();
  });

  it('releases resources and reports failure when clicking fails', () => {
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {
      throw new Error('Download unavailable');
    });

    expect(downloadFile(document, new Blob(['report']), 'report.xls')).toBe(false);
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:download-test');
    expect(document.querySelector('a[download="report.xls"]')).toBeNull();
  });

  it('reports failure when object URLs are unavailable', () => {
    vi.stubGlobal('URL', {});
    expect(downloadFile(document, new Blob(['report']), 'report.xls')).toBe(false);
  });

  it('reports failure when the object URL cannot be created', () => {
    createObjectURL.mockImplementation(() => {
      throw new Error('Object URL unavailable');
    });
    expect(downloadFile(document, new Blob(['report']), 'report.xls')).toBe(false);
    expect(revokeObjectURL).not.toHaveBeenCalled();
  });
});
