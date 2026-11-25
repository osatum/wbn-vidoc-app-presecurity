import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';
import { SimplebarAngularModule } from 'simplebar-angular';

import { TwoColumnSidebarComponent } from '../../../theme/layouts/two-column-sidebar/two-column-sidebar.component';

@Component({
  selector: 'app-ovr-two-column-sidebar',
  templateUrl: './ovr-two-column-sidebar.component.html',
  styleUrls: ['./ovr-two-column-sidebar.component.scss'],
  imports: [TranslocoPipe, RouterLink, NgbCollapse, SimplebarAngularModule],
  standalone: true,
})
export class OvrTwoColumnSidebarComponent extends TwoColumnSidebarComponent {}
