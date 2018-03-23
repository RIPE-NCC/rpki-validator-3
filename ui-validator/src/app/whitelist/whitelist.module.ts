import {NgModule} from '@angular/core';
import {FormsModule, ReactiveFormsModule} from "@angular/forms";

import {WhitelistComponent} from './whitelist.component';
import {SharedModule} from "../shared/shared.module";
import {WhitelistService} from "./whitelist.service";
import {SlurmComponent} from "./slurm/slurm.component";

@NgModule({
  imports: [
    SharedModule,
    FormsModule,
    ReactiveFormsModule
  ],
  declarations: [
    WhitelistComponent,
    SlurmComponent
  ],
  providers: [
    WhitelistService
  ]
})
export class WhitelistModule {
}
