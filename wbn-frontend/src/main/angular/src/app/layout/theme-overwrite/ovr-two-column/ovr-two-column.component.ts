import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { TwoColumnComponent } from '../../../theme/layouts/two-column/two-column.component';
import { OvrFooterComponent } from '../ovr-footer/ovr-footer.component';
import { OvrRightsidebarComponent } from '../ovr-rightsidebar/ovr-rightsidebar.component';
import { OvrSidebarComponent } from '../ovr-sidebar/ovr-sidebar.component';
import { OvrTopbarComponent } from '../ovr-topbar/ovr-topbar.component';
import { OvrTwoColumnSidebarComponent } from '../ovr-two-column-sidebar/ovr-two-column-sidebar.component';

@Component({
  selector: 'app-ovr-two-column',
  templateUrl: './ovr-two-column.component.html',
  styleUrls: ['./ovr-two-column.component.scss'],
  imports: [
    OvrTopbarComponent,
    OvrSidebarComponent,
    RouterOutlet,
    OvrRightsidebarComponent,
    OvrTwoColumnSidebarComponent,
    OvrFooterComponent,
  ],
  standalone: true,
})
export class OvrTwoColumnComponent extends TwoColumnComponent {}
