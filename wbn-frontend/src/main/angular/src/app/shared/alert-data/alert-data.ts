import { Component, input } from '@angular/core';

@Component({
  selector: 'app-aler-data',
  standalone: true,
  imports: [],
  templateUrl: './alert-data.html',
  styleUrl: './alert-data.css',
})
export class AlertData {
  readonly message = input<string | null | undefined>(undefined);
}
