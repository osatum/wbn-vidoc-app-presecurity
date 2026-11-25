import { NgClass } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';
import { SimplebarAngularModule } from 'simplebar-angular';

import { SessionExpiration } from '../../../shared/session-expiration/session-expiration';
import { SidebarComponent } from '../../../theme/layouts/sidebar/sidebar.component';

@Component({
  selector: 'app-ovr-sidebar',
  templateUrl: './ovr-sidebar.component.html',
  styleUrls: ['./ovr-sidebar.component.scss'],
  imports: [TranslocoPipe, RouterLink, NgClass, NgbCollapse, SimplebarAngularModule, SessionExpiration],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Default,
})
export class OvrSidebarComponent extends SidebarComponent {}
