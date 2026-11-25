import { Component, input } from '@angular/core';

@Component({
  selector: 'app-local-spinner',
  imports: [],
  templateUrl: './local-spinner.html',
  styleUrl: './local-spinner.css',
})
export class LocalSpinner {
  overlay = input(false); // true = przykrywa zawartość kontenera
  text = input<string | null>(null);
  height = input<number | null>(null);
}
