import {NgModule} from '@angular/core';
import {FormsModule} from "@angular/forms";

import {IgnoreFiltersComponent} from './ignore-filters.component';
import {SharedModule} from "../shared/shared.module";
import {IgnoreFiltersService} from "./ignore-filters.service";
import {PopoverMatchingAnnouncementsComponent} from "./popover-matching-announcements.component";

@NgModule({
  imports: [
    SharedModule,
    FormsModule
  ],
  declarations: [
    IgnoreFiltersComponent,
    PopoverMatchingAnnouncementsComponent
  ],
  providers: [
    IgnoreFiltersService
  ]
})
export class IgnoreFiltersModule {
}
