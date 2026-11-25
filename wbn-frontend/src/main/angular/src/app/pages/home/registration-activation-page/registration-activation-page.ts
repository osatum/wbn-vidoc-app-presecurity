import {ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal} from '@angular/core';
import {HttpErrorResponse} from '@angular/common/http';
import {NgClass} from '@angular/common';
import {ActivatedRoute, RouterLink} from '@angular/router';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {finalize} from 'rxjs';
import {TranslocoPipe, TranslocoService} from '@jsverse/transloco';

import {
  ApiErrorResponse,
  RegistrationActivationResultDto,
  RegistrationService,
} from '../../../core/backend';
import {LocalSpinner} from '../../../shared/local-spinner/local-spinner';

@Component({
  selector: 'app-registration-activation-page',
  imports: [RouterLink, NgClass, LocalSpinner, TranslocoPipe],
  templateUrl: './registration-activation-page.html',
  styleUrl: './registration-activation-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegistrationActivationPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly registrationService = inject(RegistrationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly transloco = inject(TranslocoService);

  readonly loading = signal(true);
  readonly result = signal<RegistrationActivationResultDto | null>(null);
  readonly errorMessage = signal<string | null>(null);

  readonly alertClass = computed(() => {
    switch (this.result()?.status) {
      case 'ACCOUNT_ACTIVATED':
        return 'alert-success';
      case 'WAITING_FOR_CONFIRMATION':
      case 'ACTIVATION_LINK_EXPIRED_NEW_LINK_SENT':
        return 'alert-warning';
      default:
        return 'alert-info';
    }
  });

  readonly titleKey = computed(() => {
    switch (this.result()?.status) {
      case 'ACCOUNT_ACTIVATED':
        return 'page.home.registration-activation.status.account-activated.title';
      case 'WAITING_FOR_CONFIRMATION':
        return 'page.home.registration-activation.status.waiting-for-confirmation.title';
      case 'ACTIVATION_LINK_EXPIRED_NEW_LINK_SENT':
        return 'page.home.registration-activation.status.activation-link-expired-new-link-sent.title';
      default:
        return 'page.home.registration-activation.title';
    }
  });

  readonly messageKey = computed(() => {
    switch (this.result()?.status) {
      case 'ACCOUNT_ACTIVATED':
        return 'page.home.registration-activation.status.account-activated.message';
      case 'WAITING_FOR_CONFIRMATION':
        return 'page.home.registration-activation.status.waiting-for-confirmation.message';
      case 'ACTIVATION_LINK_EXPIRED_NEW_LINK_SENT':
        return 'page.home.registration-activation.status.activation-link-expired-new-link-sent.message';
      default:
        return 'page.home.registration-activation.status.default.message';
    }
  });

  readonly showLoginLink = computed(() => this.result()?.status === 'ACCOUNT_ACTIVATED');

  ngOnInit(): void {
    const token = this.route.snapshot.paramMap.get('token');

    if (!token) {
      this.loading.set(false);
      this.errorMessage.set(this.transloco.translate('page.home.registration-activation.missing-token'));
      return;
    }

    this.registrationService
      .activateRegistration(token)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: (result) => this.result.set(result),
        error: (error: HttpErrorResponse) => this.errorMessage.set(this.extractErrorMessage(error)),
      });
  }

  private extractErrorMessage(error: HttpErrorResponse): string {
    const translationKey = this.apiError(error)?.code === 'ACTIVATION_TOKEN_ALREADY_USED'
      ? 'page.home.registration-activation.activation-token-already-used'
      : 'page.home.registration-activation.activation-error';
    return this.transloco.translate(translationKey);
  }

  private apiError(error: HttpErrorResponse): ApiErrorResponse | undefined {
    if (typeof error.error !== 'object' || error.error === null) {
      return undefined;
    }

    return error.error as ApiErrorResponse;
  }
}
