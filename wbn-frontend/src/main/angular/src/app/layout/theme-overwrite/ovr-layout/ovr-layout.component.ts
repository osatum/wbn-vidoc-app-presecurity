import { ChangeDetectionStrategy, Component } from '@angular/core';

import { LayoutComponent } from '../../../theme/layouts/layout.component';
import { OvrHorizontalComponent } from '../ovr-horizontal/ovr-horizontal.component';
import { OvrTwoColumnComponent } from '../ovr-two-column/ovr-two-column.component';
import { OvrVerticalComponent } from '../ovr-vertical/ovr-vertical.component';

@Component({
  selector: 'app-ovr-layout',
  templateUrl: './ovr-layout.component.html',
  styleUrls: ['./ovr-layout.component.scss'],
  imports: [OvrVerticalComponent, OvrHorizontalComponent, OvrTwoColumnComponent],
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Default,
})
export class OvrLayoutComponent extends LayoutComponent {}
