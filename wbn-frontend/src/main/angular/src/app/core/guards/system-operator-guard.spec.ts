import { TestBed } from '@angular/core/testing';
import { CanActivateFn } from '@angular/router';
import {systemOperatorGuard} from './system-operator-guard';



describe('systemOperatorGuardGuard', () => {
  const executeGuard: CanActivateFn = (...guardParameters) =>
    TestBed.runInInjectionContext(() => systemOperatorGuard(...guardParameters));

  beforeEach(() => {
    TestBed.configureTestingModule({});
  });

  it('should be created', () => {
    expect(executeGuard).toBeTruthy();
  });
});
