import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

export interface InstitutionDeletionConfirmation {
  confirmationId: string;
  confirmationCode: string;
  expiresAt: string;
}

export interface InstitutionDeletionConfirmationForm {
  confirmationId: string;
  confirmationCode: string;
}

@Injectable({
  providedIn: 'root',
})
export class InstitutionDeletionService {
  private readonly http = inject(HttpClient);

  createConfirmation(institutionId: number): Observable<InstitutionDeletionConfirmation> {
    return this.http.post<InstitutionDeletionConfirmation>(
      environment.apiBasePath + '/api/institution/' + institutionId + '/deletion-confirmations',
      {},
    );
  }

  deleteInstitution(
    institutionId: number,
    form: InstitutionDeletionConfirmationForm,
  ): Observable<void> {
    return this.http.delete<void>(environment.apiBasePath + '/api/institution/' + institutionId, {
      body: form,
    });
  }
}
