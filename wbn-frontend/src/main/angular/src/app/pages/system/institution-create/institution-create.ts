import { ChangeDetectionStrategy, Component } from '@angular/core';
import { InstitutionForm } from '../institution-form/institution-form';

@Component({
  selector: 'app-institution-create',
  standalone: true,
  imports: [InstitutionForm],
  templateUrl: './institution-create.html',
  styleUrl: './institution-create.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstitutionCreate {}
