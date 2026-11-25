import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { ApiErrorResponse, UserDataDeletionService, UserDataDeletionTokenStatusDto } from '../../../core/backend';
import { AuthService } from '../../../core/services/auth.service';
import { LocalSpinner } from '../../../shared/local-spinner/local-spinner';

type TokenStatus = UserDataDeletionTokenStatusDto.StatusEnum;

@Component({
  selector: 'app-user-data-deletion-confirm',
  standalone: true,
  imports: [RouterLink, LocalSpinner, TranslocoPipe],
  templateUrl: './user-data-deletion-confirm.html',
  styleUrl: './user-data-deletion-confirm.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserDataDeletionConfirmPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly userDataDeletionService = inject(UserDataDeletionService);
  private readonly authService = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly transloco = inject(TranslocoService);

  readonly loading = signal(true);
  readonly saving = signal(false);
  readonly success = signal(false);
  readonly tokenStatus = signal<TokenStatus | null>(null);
  readonly errorMessage = signal<string | null>(null);
  readonly canConfirm = computed(() =>
    !this.loading() && !this.success() && this.tokenStatus() === UserDataDeletionTokenStatusDto.StatusEnum.Valid
  );
  readonly statusMessage = computed(() => this.messageForStatus(this.tokenStatus()));

  private token = '';

  ngOnInit(): void {
    this.token = this.route.snapshot.paramMap.get('token') ?? '';

    if (!this.token) {
      this.loading.set(false);
      this.tokenStatus.set(UserDataDeletionTokenStatusDto.StatusEnum.Invalid);
      return;
    }

    this.userDataDeletionService.verifyUserDataDeletionToken(this.token)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.loading.set(false)),
      )
      .subscribe({
        next: result => this.tokenStatus.set(result.status ?? UserDataDeletionTokenStatusDto.StatusEnum.Invalid),
        error: (error: HttpErrorResponse) => this.errorMessage.set(this.extractErrorMessage(error)),
      });
  }

  confirm(): void {
    if (this.saving() || !this.canConfirm()) {
      return;
    }

    this.errorMessage.set(null);
    this.saving.set(true);
    this.userDataDeletionService.confirmUserDataDeletion(this.token)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.saving.set(false)),
      )
      .subscribe({
        next: result => this.applyConfirmResult(result.status),
        error: (error: HttpErrorResponse) => this.errorMessage.set(this.extractErrorMessage(error)),
      });
  }

  private applyConfirmResult(status: TokenStatus | undefined): void {
    const resolvedStatus = status ?? UserDataDeletionTokenStatusDto.StatusEnum.Invalid;
    this.tokenStatus.set(resolvedStatus);

    if (resolvedStatus === UserDataDeletionTokenStatusDto.StatusEnum.Completed) {
      this.authService.clearSession();
      this.success.set(true);
      return;
    }

    this.errorMessage.set(this.messageForStatus(resolvedStatus));
  }

  private messageForStatus(status: TokenStatus | null): string | null {
    switch (status) {
      case UserDataDeletionTokenStatusDto.StatusEnum.Invalid:
        return this.transloco.translate('page.auth.userDataDeletionConfirm.invalid');
      case UserDataDeletionTokenStatusDto.StatusEnum.Expired:
        return this.transloco.translate('page.auth.userDataDeletionConfirm.expired');
      case UserDataDeletionTokenStatusDto.StatusEnum.Used:
        return this.transloco.translate('page.auth.userDataDeletionConfirm.used');
      default:
        return null;
    }
  }

  private extractErrorMessage(error: HttpErrorResponse): string {
    const body = this.apiError(error);

    if (error.status >= 500) {
      return this.transloco.translate('common.serverError', { status: error.status });
    }

    if (error.status === 0) {
      return this.transloco.translate('common.connectionError');
    }

    return body?.message ?? this.transloco.translate('page.auth.userDataDeletionConfirm.failed');
  }

  private apiError(error: HttpErrorResponse): ApiErrorResponse | undefined {
    if (typeof error.error !== 'object' || error.error === null) {
      return undefined;
    }

    return error.error as ApiErrorResponse;
  }
}
