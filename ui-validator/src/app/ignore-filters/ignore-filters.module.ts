import {NgModule} from '@angular/core';
import {FormsModule} from "@angular/forms";

import {IgnoreFiltersComponent} from './ignore-filters.component';
import {SharedModule} from "../shared/shared.module";
import {IgnoreFiltersService} from "./ignore-filters.service";

@NgModule({
  imports: [
    SharedModule,
    FormsModule
  ],
  declarations: [
    IgnoreFiltersComponent
  ],
  providers: [
    IgnoreFiltersService
  ]
})
export class IgnoreFiltersModule {
}
