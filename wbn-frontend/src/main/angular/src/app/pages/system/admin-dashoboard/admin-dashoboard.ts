import { Component } from '@angular/core';
import { DashboardCardinality } from '../dashboard-cardinality/dashboard-cardinality';
import { DashboardCharts } from '../dashboard-charts/dashboard-charts';
import { DashboardNavigationAuditKpi } from '../dashboard-navigation-audit-kpi/dashboard-navigation-audit-kpi';
import { DashboardSessionList } from '../dashboard-session-list/dashboard-session-list';

@Component({
  selector: 'app-admin-dashoboard',
  imports: [DashboardCardinality, DashboardNavigationAuditKpi, DashboardCharts, DashboardSessionList],
  templateUrl: './admin-dashoboard.html',
  styleUrl: './admin-dashoboard.css',
})
export class AdminDashoboard {

}
