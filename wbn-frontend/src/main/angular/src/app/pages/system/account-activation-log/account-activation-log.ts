import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { AccountActivationLogList } from '../account-activation-log-list/account-activation-log-list';

@Component({
  selector: 'app-account-activation-log',
  standalone: true,
  imports: [AccountActivationLogList, TranslocoPipe],
  templateUrl: './account-activation-log.html',
  styleUrl: './account-activation-log.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountActivationLog {}
