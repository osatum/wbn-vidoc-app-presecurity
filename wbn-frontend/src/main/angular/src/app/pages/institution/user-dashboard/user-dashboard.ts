import { Component } from '@angular/core';
import { JournalList } from '../journal-list/journal-list';

@Component({
  selector: 'app-user-dashboard',
  imports: [JournalList],
  templateUrl: './user-dashboard.html',
  styleUrl: './user-dashboard.css',
})
export class UserDashboard {}
