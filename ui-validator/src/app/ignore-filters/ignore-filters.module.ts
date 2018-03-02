import {NgModule} from '@angular/core';
import {FormsModule} from "@angular/forms";

import {IgnoreFiltersComponent} from './ignore-filters.component';
import {SharedModule} from "../shared/shared.module";

@NgModule({
  imports: [
    SharedModule,
    FormsModule
  ],
  declarations: [
    IgnoreFiltersComponent
  ]
})
export class IgnoreFiltersModule {
}
