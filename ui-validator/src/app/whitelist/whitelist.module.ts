import {NgModule} from '@angular/core';
import {FormsModule} from "@angular/forms";

import {WhitelistComponent} from './whitelist.component';
import {SharedModule} from "../shared/shared.module";
import {WhitelistService} from "./whitelist.service";

@NgModule({
  imports: [
    SharedModule,
    FormsModule
  ],
  declarations: [
    WhitelistComponent
  ],
  providers: [
    WhitelistService
  ]
})
export class WhitelistModule {
}
