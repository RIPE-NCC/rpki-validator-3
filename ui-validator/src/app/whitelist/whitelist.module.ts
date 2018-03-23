import {NgModule} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from "@angular/forms";

import {WhitelistComponent} from './whitelist.component';
import {SharedModule} from "../shared/shared.module";
import {WhitelistService} from "./whitelist.service";
import {SlurmComponent} from "./slurm/slurm.component";
import {PopoverEntryComponent} from "./popover-entry-details.component";

@NgModule({
  imports: [
    SharedModule,
    FormsModule,
    ReactiveFormsModule
  ],
  declarations: [
    WhitelistComponent,
    SlurmComponent,
    PopoverEntryComponent
  ],
  providers: [
    WhitelistService
  ]
})
export class WhitelistModule {
}
