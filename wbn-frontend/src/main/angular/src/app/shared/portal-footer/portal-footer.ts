import { Component, inject, OnInit, signal } from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

@Component({
  selector: 'app-portal-footer',
  imports: [TranslocoPipe],
  templateUrl: './portal-footer.html',
  styleUrl: './portal-footer.css',
})
export class PortalFooter implements OnInit {
  // set the current year
  year: number = new Date().getFullYear();
  private readonly translocoService: TranslocoService = inject(TranslocoService);
  private readonly lang = signal(this.translocoService.getActiveLang());

  ngOnInit(): void {
    this.translocoService.langChanges$.subscribe(() => {
      this.lang.set(this.translocoService.getActiveLang());
    });
  }

  isPl(){
    if (this.lang() === 'pl')
      return true;
    return false;
  }

}
