import {Component} from '@angular/core';
import {AbstractSelect} from '../abstract-select/abstract-select';
import { InstitutionAccessDto, InstitutionMembershipAccessDto } from '../../../core/backend';
import { TranslocoPipe } from '@jsverse/transloco';
import {
  SYSTEM_INSTITUTION_ROLE_LABELS,
  SYSTEM_INSTITUTION_TYPE_LABELS,
} from '../../shared/system-ui-definitions';

@Component({
  selector: 'app-select-membership',
  imports: [TranslocoPipe],
  templateUrl: './select-membership.html',
  styleUrl: './select-membership.css',
})
export class SelectMembership extends AbstractSelect {
  institutionTypeLabelKey(type: InstitutionAccessDto.InstitutionTypeEnum | undefined): string {
    return type ? SYSTEM_INSTITUTION_TYPE_LABELS[type] ?? '-' : '-';
  }

  roleLabelKey(role: InstitutionMembershipAccessDto.RoleEnum | undefined): string {
    return role ? SYSTEM_INSTITUTION_ROLE_LABELS[role] ?? '-' : '-';
  }
}
