import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { provideTransloco, TranslocoLoader, Translation } from '@jsverse/transloco';
import { of } from 'rxjs';
import { UserService } from '../../../core/backend';
import { ToastService } from '../../../core/services/toast-service';
import { UserDetails } from './user-details';

class TestingTranslocoLoader implements TranslocoLoader {
  getTranslation() {
    return of({} as Translation);
  }
}

describe('UserDetails', () => {
  let component: UserDetails;
  let fixture: ComponentFixture<UserDetails>;
  let userService: {
    findUser: ReturnType<typeof vi.fn>;
    deleteUser: ReturnType<typeof vi.fn>;
  };
  let router: Router;
  let toastService: { show: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    userService = {
      findUser: vi.fn(() => of({ id: 1, email: 'test@example.com' })),
      deleteUser: vi.fn(() => of(null)),
    };
    toastService = { show: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [UserDetails],
      providers: [
        provideRouter([]),
        provideTransloco({
          config: {
            availableLangs: ['pl'],
            defaultLang: 'pl',
            prodMode: true,
          },
          loader: TestingTranslocoLoader,
        }),
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: of(convertToParamMap({ id: '1' })),
          },
        },
        { provide: UserService, useValue: userService },
        { provide: ToastService, useValue: toastService },
      ],
    }).compileComponents();

    router = TestBed.inject(Router);
    vi.spyOn(router, 'navigate').mockResolvedValue(true);

    fixture = TestBed.createComponent(UserDetails);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should delete user and navigate to user list', async () => {
    const modal = {
      close: vi.fn(),
      dismiss: vi.fn(),
    };

    component.deleteUser(modal);
    fixture.detectChanges();
    await fixture.whenStable();

    expect(userService.deleteUser).toHaveBeenCalledWith(1);
    expect(modal.close).toHaveBeenCalledWith('deleted');
    expect(toastService.show).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/system/admin/users']);
  });
});
