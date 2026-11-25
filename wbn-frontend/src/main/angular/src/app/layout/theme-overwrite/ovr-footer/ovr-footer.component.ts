import { Component } from '@angular/core';

import { FooterComponent } from '../../../theme/layouts/footer/footer.component';

@Component({
  selector: 'app-ovr-footer',
  templateUrl: './ovr-footer.component.html',
  styleUrls: ['./ovr-footer.component.scss'],
  standalone: true,
})
export class OvrFooterComponent extends FooterComponent {}
