import { ChangeDetectionStrategy, Component } from '@angular/core';
import { LoginSessionList } from '../login-session-list/login-session-list';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'app-login-session',
  standalone: true,
  imports: [LoginSessionList, TranslocoPipe],
  templateUrl: './login-session.html',
  styleUrl: './login-session.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginSession {}
