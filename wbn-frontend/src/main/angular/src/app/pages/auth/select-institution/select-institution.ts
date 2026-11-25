import {Component} from '@angular/core';
import {AbstractSelect} from '../abstract-select/abstract-select';
import { InstitutionAccessDto, InstitutionMembershipAccessDto } from '../../../core/backend';
import { NgClass } from '@angular/common';
import { TranslocoPipe } from '@jsverse/transloco';
import {
  SYSTEM_INSTITUTION_ROLE_LABELS,
  SYSTEM_INSTITUTION_TYPE_LABELS,
} from '../../shared/system-ui-definitions';

@Component({
  selector: 'app-select-institution',
  imports: [NgClass, TranslocoPipe],
  templateUrl: './select-institution.html',
  styleUrl: './select-institution.css',
})
export class SelectInstitution extends AbstractSelect {
  isOperator(institution: InstitutionAccessDto) {
    return InstitutionAccessDto.InstitutionTypeEnum.SystemOperator === institution.institutionType;
  }

  institutionTypeLabelKey(type: InstitutionAccessDto.InstitutionTypeEnum | undefined): string {
    return type ? SYSTEM_INSTITUTION_TYPE_LABELS[type] ?? '-' : '-';
  }

  roleLabelKey(role: InstitutionMembershipAccessDto.RoleEnum | undefined): string {
    return role ? SYSTEM_INSTITUTION_ROLE_LABELS[role] ?? '-' : '-';
  }
}
