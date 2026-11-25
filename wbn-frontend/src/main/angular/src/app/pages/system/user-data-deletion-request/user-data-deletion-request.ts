import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { UserDataDeletionRequestList } from '../user-data-deletion-request-list/user-data-deletion-request-list';

@Component({
  selector: 'app-user-data-deletion-request',
  standalone: true,
  imports: [UserDataDeletionRequestList, TranslocoPipe],
  templateUrl: './user-data-deletion-request.html',
  styleUrl: './user-data-deletion-request.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserDataDeletionRequest {}
