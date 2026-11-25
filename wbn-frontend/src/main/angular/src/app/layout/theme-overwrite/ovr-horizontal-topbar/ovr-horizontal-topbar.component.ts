import { NgClass } from '@angular/common';
import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { HorizontalTopbarComponent } from '../../../theme/layouts/horizontal-topbar/horizontal-topbar.component';

@Component({
  selector: 'app-ovr-horizontal-topbar',
  templateUrl: './ovr-horizontal-topbar.component.html',
  styleUrls: ['./ovr-horizontal-topbar.component.scss'],
  imports: [TranslocoPipe, NgClass, RouterLink],
  standalone: true,
})
export class OvrHorizontalTopbarComponent extends HorizontalTopbarComponent {}
