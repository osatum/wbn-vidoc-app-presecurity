import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { PasswordResetRequestList } from '../password-reset-request-list/password-reset-request-list';

@Component({
  selector: 'app-password-reset-request',
  standalone: true,
  imports: [PasswordResetRequestList, TranslocoPipe],
  templateUrl: './password-reset-request.html',
  styleUrl: './password-reset-request.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PasswordResetRequest {}
