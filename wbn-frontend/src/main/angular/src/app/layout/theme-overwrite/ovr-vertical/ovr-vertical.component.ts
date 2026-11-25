import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { VerticalComponent } from '../../../theme/layouts/vertical/vertical.component';
import { OvrFooterComponent } from '../ovr-footer/ovr-footer.component';
import { OvrRightsidebarComponent } from '../ovr-rightsidebar/ovr-rightsidebar.component';
import { OvrSidebarComponent } from '../ovr-sidebar/ovr-sidebar.component';
import { OvrTopbarComponent } from '../ovr-topbar/ovr-topbar.component';

@Component({
  selector: 'app-ovr-vertical',
  templateUrl: './ovr-vertical.component.html',
  styleUrls: ['./ovr-vertical.component.scss'],
  imports: [
    OvrTopbarComponent,
    RouterOutlet,
    OvrFooterComponent,
    OvrRightsidebarComponent,
    OvrSidebarComponent,
  ],
  standalone: true,
})
export class OvrVerticalComponent extends VerticalComponent {}
