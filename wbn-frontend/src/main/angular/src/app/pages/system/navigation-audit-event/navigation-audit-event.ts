import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { NgbNavModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslocoPipe } from '@jsverse/transloco';
import { NavigationAuditEventList } from '../navigation-audit-event-list/navigation-audit-event-list';
import { NavigationAuditReports } from '../navigation-audit-reports/navigation-audit-reports';
import { NavigationAuditVisitList } from '../navigation-audit-visit-list/navigation-audit-visit-list';

@Component({
  selector: 'app-navigation-audit-event',
  standalone: true,
  imports: [
    NavigationAuditEventList,
    NavigationAuditReports,
    NavigationAuditVisitList,
    NgbNavModule,
    TranslocoPipe,
  ],
  templateUrl: './navigation-audit-event.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NavigationAuditEventLog {
  readonly activeTab = signal(1);
}
