import { ChangeDetectionStrategy, Component } from '@angular/core';
import { LoginSessionList } from '../login-session-list/login-session-list';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-active-session',
  standalone: true,
  imports: [LoginSessionList, TranslocoPipe],
  templateUrl: './active-session.html',
  styleUrl: './active-session.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ActiveSession {}
