import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { environment } from '../../../environments/environment';
import { InstitutionDeletionService } from './institution-deletion.service';

describe('InstitutionDeletionService', () => {
  let service: InstitutionDeletionService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(InstitutionDeletionService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('creates a deletion confirmation for an institution', () => {
    const response = {
      confirmationId: '9a21f5a0-5d0e-49e8-9f96-0a540c21e2de',
      confirmationCode: 'AB34CD56',
      expiresAt: '2026-08-04T20:05:00Z',
    };

    service.createConfirmation(12).subscribe((result) => expect(result).toEqual(response));

    const request = httpMock.expectOne(
      environment.apiBasePath + '/api/institution/12/deletion-confirmations',
    );
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({});
    request.flush(response);
  });

  it('sends the confirmation when deleting an institution', () => {
    const form = {
      confirmationId: '9a21f5a0-5d0e-49e8-9f96-0a540c21e2de',
      confirmationCode: 'AB34CD56',
    };

    service.deleteInstitution(12, form).subscribe();

    const request = httpMock.expectOne(environment.apiBasePath + '/api/institution/12');
    expect(request.request.method).toBe('DELETE');
    expect(request.request.body).toEqual(form);
    request.flush(null);
  });
});
