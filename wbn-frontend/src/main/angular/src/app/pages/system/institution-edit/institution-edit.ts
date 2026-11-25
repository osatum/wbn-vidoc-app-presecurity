import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { TranslocoPipe } from '@jsverse/transloco';
import { InstitutionForm } from '../institution-form/institution-form';

@Component({
  selector: 'app-institution-edit',
  standalone: true,
  imports: [InstitutionForm, TranslocoPipe],
  templateUrl: './institution-edit.html',
  styleUrl: './institution-edit.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InstitutionEdit {
  private readonly route = inject(ActivatedRoute);

  readonly institutionId = toSignal(
    this.route.paramMap.pipe(map((params) => this.parseInstitutionId(params.get('id')))),
    { initialValue: null },
  );

  private parseInstitutionId(value: string | null): number | null {
    const id = Number(value);
    return Number.isInteger(id) && id > 0 ? id : null;
  }
}
