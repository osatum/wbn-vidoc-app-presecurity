import {Component, inject, signal} from '@angular/core';
import {AuthService} from '../../../core/services/auth.service';
import {RedirectService} from '../../../core/services/redirect-service';
import {ToastService} from '../../../core/services/toast-service';
import {InstitutionAccessDto} from '../../../core/backend';

@Component({
  selector: 'app-abstract-select',
  template: '',
})
export class AbstractSelect {
  protected authService = inject(AuthService);
  protected redirectService = inject(RedirectService);
  protected toastService = inject(ToastService);

  loading = false;
  error: string | null = null;

  protected readonly institutions = signal<InstitutionAccessDto[] | null>(null);

  constructor() {
    console.log('snapshot', this.authService.getPendingInstitutionsSnapshot());
    this.institutions.set(this.authService.getPendingInstitutionsSnapshot() ?? []);
    console.log('institutions after set', this.institutions());
  }

  async select(institutionId: number | undefined, membershipId: number | undefined) {
    this.authService.selectInstitution(institutionId!, membershipId!).subscribe({
      next: response => this.redirectService.redirectAfterLogin(response),
      error: error => {
        this.error = error?.error?.detail ?? 'Login failed';
        this.loading = false;
        this.toastService.show(error.message ?? 'Login failed', { classname: 'bg-danger text-white', delay: 15000 });
      },
      complete: () => this.loading = false
    });
  }
}
