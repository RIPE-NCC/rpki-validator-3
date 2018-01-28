import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RoasListComponent } from './roas-list.component';
import { TranslateModule } from "@ngx-translate/core";
import { RoasService } from "./roas.service";

@NgModule({
  imports: [
    CommonModule,
    TranslateModule
  ],
  declarations: [RoasListComponent],
  providers: [
    RoasService
  ]
})
export class RoasModule { }
