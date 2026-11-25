import { Component } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'app-danied',
  imports: [TranslocoPipe, RouterLink],
  templateUrl: './denied.html',
  styleUrl: './denied.css',
})
export class Denied {}
