import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../environments/environment';
import { OperatingProfileService } from './operating-profile.service';

describe('OperatingProfileService', () => {
  it('uses authenticated actor context without allowing a client-provided institution or profile override', () => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    const service = TestBed.inject(OperatingProfileService);
    const http = TestBed.inject(HttpTestingController);
    service.currentContext().subscribe();
    const context = http.expectOne(
      environment.apiBasePath + '/api/configuration/operating-profile',
    );
    expect(context.request.method).toBe('GET');
    expect(context.request.params.keys()).toEqual([]);
    expect(context.request.transferCache).toBe(false);
    context.flush({
      institutionId: 10,
      effectiveProfile: 'BOTH',
      defaultSearchView: 'ADVANCED',
      availableFeatures: ['BASIC_SEARCH', 'ADVANCED_SEARCH'],
    });
    service.defaultProfile().subscribe();
    http
      .expectOne(environment.apiBasePath + '/api/configuration/operating-profile/default')
      .flush('BOTH');
    http.verify();
  });
});
