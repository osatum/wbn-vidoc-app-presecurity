export function downloadFile(document: Document, file: Blob, filename: string): boolean {
  const urlApi = document.defaultView?.URL;
  if (!urlApi || typeof urlApi.createObjectURL !== 'function') {
    return false;
  }

  try {
    const objectUrl = urlApi.createObjectURL(file);
    try {
      const link = document.createElement('a');
      link.href = objectUrl;
      link.download = filename;
      try {
        document.body.appendChild(link);
        link.click();
      } finally {
        link.remove();
      }
    } finally {
      urlApi.revokeObjectURL(objectUrl);
    }
    return true;
  } catch {
    return false;
  }
}
