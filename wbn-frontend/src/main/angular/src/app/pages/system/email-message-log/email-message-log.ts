import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { EmailMessageLogList } from '../email-message-log-list/email-message-log-list';

@Component({
  selector: 'app-email-message-log',
  standalone: true,
  imports: [EmailMessageLogList, TranslocoPipe],
  templateUrl: './email-message-log.html',
  styleUrl: './email-message-log.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmailMessageLog {}
