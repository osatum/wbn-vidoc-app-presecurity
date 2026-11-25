import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslocoPipe } from '@jsverse/transloco';
import { OperatingProfile } from '../../../core/backend';
import {
  OPERATING_PROFILES,
  OperatingProfileService,
} from '../../../core/services/operating-profile.service';

@Component({
  selector: 'app-operating-profile-setting',
  imports: [TranslocoPipe],
  template: `
    @if (editable()) {
      <label for="operatingProfile" class="form-label">{{
        'operatingProfile.label' | transloco
      }}</label>
      <select
        id="operatingProfile"
        class="form-select"
        [value]="value() ?? ''"
        (change)="select($event)"
      >
        <option value="">{{ 'operatingProfile.inherit' | transloco }}</option>
        @for (profile of profiles; track profile) {
          <option [value]="profile">{{ 'operatingProfile.profile.' + profile | transloco }}</option>
        }
      </select>
    } @else {
      <strong class="d-block">{{ 'operatingProfile.label' | transloco }}</strong>
      <span>{{
        (value() ? 'operatingProfile.profile.' + value() : 'operatingProfile.inherit') | transloco
      }}</span>
    }
    @if (defaultProfile(); as profile) {
      <div class="small mt-2" data-testid="operating-profile-effective">
        {{ 'operatingProfile.default' | transloco }}:
        {{ 'operatingProfile.profile.' + profile | transloco }}.
        {{ 'operatingProfile.effective' | transloco }}:
        {{ 'operatingProfile.profile.' + effectiveProfile() | transloco }} ({{
          (value() ? 'operatingProfile.unitSource' : 'operatingProfile.systemSource') | transloco
        }}).
      </div>
    } @else if (error()) {
      <p class="text-danger mt-2" role="alert">
        {{ 'operatingProfile.defaultError' | transloco }}
        <button type="button" class="btn btn-link" (click)="loadDefault()">
          {{ 'page.institution.journalList.retrySearch' | transloco }}
        </button>
      </p>
    } @else {
      <span class="d-block small mt-2" role="status">{{
        'operatingProfile.loading' | transloco
      }}</span>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OperatingProfileSetting {
  private readonly api = inject(OperatingProfileService);
  private readonly destroyRef = inject(DestroyRef);
  readonly value = input<OperatingProfile | null>(null);
  readonly editable = input(false);
  readonly valueChange = output<OperatingProfile | null>();
  readonly profiles = OPERATING_PROFILES;
  readonly defaultProfile = signal<OperatingProfile | null>(null);
  readonly effectiveProfile = computed(() => this.value() ?? this.defaultProfile());
  readonly error = signal(false);

  constructor() {
    this.loadDefault();
  }

  loadDefault(): void {
    this.error.set(false);
    this.api
      .defaultProfile()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (profile) => {
          if (this.profiles.includes(profile)) this.defaultProfile.set(profile);
          else this.error.set(true);
        },
        error: () => this.error.set(true),
      });
  }

  select(event: Event): void {
    const profile = (event.target as HTMLSelectElement).value;
    if (!profile) this.valueChange.emit(null);
    else if (this.profiles.some((option) => option === profile))
      this.valueChange.emit(profile as OperatingProfile);
  }
}
