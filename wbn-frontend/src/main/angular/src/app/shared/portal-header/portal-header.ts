import { ChangeDetectionStrategy, Component } from '@angular/core';
import { AsyncPipe, NgClass } from '@angular/common';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { DefaultHeader } from '../default-header/default-header';
import { TranslocoPipe } from '@jsverse/transloco';

type ThemeMode = 'light' | 'dark';

@Component({
  selector: 'app-portal-header',
  imports: [AsyncPipe, NgbModule, NgClass, TranslocoPipe],
  templateUrl: './portal-header.html',
  styleUrl: './portal-header.css',
  changeDetection: ChangeDetectionStrategy.Default,
})
export class PortalHeader extends DefaultHeader {}
