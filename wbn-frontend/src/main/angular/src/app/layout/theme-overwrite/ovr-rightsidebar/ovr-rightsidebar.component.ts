import { ChangeDetectionStrategy, Component } from '@angular/core';
import { SimplebarAngularModule } from 'simplebar-angular';

import { RightsidebarComponent } from '../../../theme/layouts/rightsidebar/rightsidebar.component';

@Component({
  selector: 'app-ovr-rightsidebar',
  templateUrl: './ovr-rightsidebar.component.html',
  styleUrls: ['./ovr-rightsidebar.component.scss'],
  imports: [SimplebarAngularModule],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Default,
})
export class OvrRightsidebarComponent extends RightsidebarComponent {}
