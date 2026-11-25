import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { UserLookupList } from '../user-lookup-list/user-lookup-list';
import { TranslocoPipe } from '@jsverse/transloco';
import { UserListQuery } from '../../../core/backend';

@Component({
  selector: 'app-user-list',
  standalone: true,
  imports: [UserLookupList, TranslocoPipe],
  templateUrl: './user-list.html',
  styleUrl: './user-list.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UserList {
  readonly sortOptions: { value: UserListQuery.SortByEnum; labelKey: string }[] = [
    {
      value: UserListQuery.SortByEnum.SortByNameAsc,
      labelKey: 'page.system.user-list.sortOptions.nameAsc',
    },
    {
      value: UserListQuery.SortByEnum.SortByNameDesc,
      labelKey: 'page.system.user-list.sortOptions.nameDesc',
    },
    {
      value: UserListQuery.SortByEnum.SortByEmailAsc,
      labelKey: 'page.system.user-list.sortOptions.emailAsc',
    },
    {
      value: UserListQuery.SortByEnum.SortByEmailDesc,
      labelKey: 'page.system.user-list.sortOptions.emailDesc',
    },
  ];
  readonly sortBy = signal<UserListQuery.SortByEnum>(
    UserListQuery.SortByEnum.SortByNameAsc,
  );

  changeSortBy(event: Event): void {
    const value = (event.target as HTMLSelectElement).value as UserListQuery.SortByEnum;

    if (this.sortOptions.some((option) => option.value === value)) {
      this.sortBy.set(value);
    }
  }
}
