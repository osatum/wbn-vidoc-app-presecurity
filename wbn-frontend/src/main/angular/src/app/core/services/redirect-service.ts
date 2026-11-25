import { Injectable } from '@angular/core';
import {AuthResponse, UserProfileResponse} from '../backend';
import {Router} from '@angular/router';


@Injectable({
  providedIn: 'root',
})
export class RedirectService {

  constructor(
    private router: Router
  ) {
  }

  redirectAfterLogin(response: AuthResponse): void {
    if (AuthResponse.StatusEnum.InstitutionRequired === response.status) {
      this.router.navigate(['/auth/select-institutions']);
      return;
    }
    if (AuthResponse.StatusEnum.MembershipRequired === response.status) {
      this.router.navigate(['/auth/select-membership']);
      return;
    }
    if (AuthResponse.StatusEnum.Ok === response.status) {
      if (AuthResponse.InstitutionTypeEnum.SystemOperator === response.institutionType){
        if (AuthResponse.InstitutionRoleEnum.InstitutionAdmin === response.institutionRole) {
          this.router.navigate(['/system/admin/dashboard']);
          return;
        }
        this.router.navigate(['/']);
        return;
      }

      console.log(response);

      if (AuthResponse.InstitutionTypeEnum.ResearchUnit === response.institutionType){
        if (AuthResponse.InstitutionRoleEnum.InstitutionAdmin === response.institutionRole) {
          this.router.navigate(['/institution/admin/dashboard']);
          return;
        }
        if (AuthResponse.InstitutionRoleEnum.InstitutionViewer=== response.institutionRole) {
          this.router.navigate(['/institution/user/dashboard']);
          console.log('/institution/user/dashboard');
          return;
        }
        if (AuthResponse.InstitutionRoleEnum.InstitutionUser=== response.institutionRole) {
          this.router.navigate(['/institution/user/dashboard']);
          return;
        }
        this.router.navigate(['/']);
        return;
      }
      this.router.navigate(['/secured']);
      return;
    }
    // fallback jeśli nie ma dopasowania
    this.router.navigate(['/']);
  }

  redirectToDashboard(userProfile : UserProfileResponse | null) {
    console.log('Redirecting to dashboard for user profile:', userProfile);
    if (userProfile === null){
      return;
    }
    if (UserProfileResponse.InstitutionTypeEnum.SystemOperator === userProfile.institutionType){
      if (UserProfileResponse.InstitutionRoleEnum.InstitutionAdmin === userProfile.institutionRole) {
        this.router.navigate(['/system/admin/dashboard']);
        return;
      }
      this.router.navigate(['/']);
      return;
    }
    if (UserProfileResponse.InstitutionTypeEnum.ResearchUnit === userProfile.institutionType){
      if (AuthResponse.InstitutionRoleEnum.InstitutionAdmin === userProfile.institutionRole) {
        this.router.navigate(['/institution/admin/dashboard']);
        return;
      }
      if (UserProfileResponse.InstitutionRoleEnum.InstitutionViewer=== userProfile.institutionRole) {
        this.router.navigate(['/institution/user/dashboard']);
        console.log('/institution/user/dashboard');
        return;
      }
      if (UserProfileResponse.InstitutionRoleEnum.InstitutionUser=== userProfile.institutionRole) {
        this.router.navigate(['/institution/user/dashboard']);
        return;
      }
      this.router.navigate(['/']);
      return;
    }
  }

  redirectToDenied(profile:UserProfileResponse | null) {
    if (profile === null){
      return this.router.createUrlTree(['/denied'])
    }

    if (UserProfileResponse.InstitutionTypeEnum.ResearchUnit === profile.institutionType){
      return this.router.createUrlTree(['/institution/user/denied'])
    }
    if (UserProfileResponse.InstitutionTypeEnum.SystemOperator === profile.institutionType){
      return this.router.createUrlTree(['/system/admin/denied'])
    }
    return this.router.createUrlTree(['/denied']);
  }

}
