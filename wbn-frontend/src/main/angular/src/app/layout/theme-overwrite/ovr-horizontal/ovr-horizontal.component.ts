import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { HorizontalComponent } from '../../../theme/layouts/horizontal/horizontal.component';
import { OvrFooterComponent } from '../ovr-footer/ovr-footer.component';
import { OvrHorizontalTopbarComponent } from '../ovr-horizontal-topbar/ovr-horizontal-topbar.component';
import { OvrRightsidebarComponent } from '../ovr-rightsidebar/ovr-rightsidebar.component';
import { OvrTopbarComponent } from '../ovr-topbar/ovr-topbar.component';

@Component({
  selector: 'app-ovr-horizontal',
  templateUrl: './ovr-horizontal.component.html',
  styleUrls: ['./ovr-horizontal.component.scss'],
  imports: [
    OvrTopbarComponent,
    OvrHorizontalTopbarComponent,
    OvrFooterComponent,
    OvrRightsidebarComponent,
    RouterOutlet,
  ],
  standalone: true,
})
export class OvrHorizontalComponent extends HorizontalComponent {}
