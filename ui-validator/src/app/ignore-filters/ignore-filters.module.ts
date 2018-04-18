import {NgModule} from '@angular/core';
import {FormsModule} from '@angular/forms';

import {IgnoreFiltersComponent} from './ignore-filters.component';
import {SharedModule} from '../shared/shared.module';
import {IgnoreFiltersService} from './ignore-filters.service';
import {PopoverAffectedRoasComponent} from './popover-affected-roas.component';

@NgModule({
  imports: [
    SharedModule,
    FormsModule
  ],
  declarations: [
    IgnoreFiltersComponent,
    PopoverAffectedRoasComponent
  ],
  providers: [
    IgnoreFiltersService
  ]
})
export class IgnoreFiltersModule {
}
