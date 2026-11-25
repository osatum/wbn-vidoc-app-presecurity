import {Injectable, signal, TemplateRef} from '@angular/core';

@Injectable({providedIn: 'root'})
export class ToastService {
  toasts = signal<any[]>([]);

  show(textOrTpl: string | TemplateRef<any>, options: any = {}) {
    this.toasts.update(list => [...list, { textOrTpl, ...options }]);
  }

  remove(toast: any) {
    this.toasts.update(list => list.filter(t => t !== toast));
  }
}
