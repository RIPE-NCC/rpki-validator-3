import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HomeComponent } from './home.component';
import { TranslateModule } from '@ngx-translate/core';
import { NgbModule } from "@ng-bootstrap/ng-bootstrap";

@NgModule({
  imports: [
    CommonModule,
    TranslateModule,
    NgbModule
  ],
  declarations: [
    HomeComponent
  ]
})
export class HomeModule { }
