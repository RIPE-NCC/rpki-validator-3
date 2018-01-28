import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HomeComponent } from './home.component';
import { RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';

@NgModule({
  imports: [
    CommonModule,
    // RouterModule.forRoot([
    //   { path: 'home', component: HomeComponent},
    //   { path: '**', redirectTo: 'home', pathMatch: 'full'}
    // ]),
    TranslateModule
  ],
  declarations: [
    HomeComponent
  ]
})
export class HomeModule { }
