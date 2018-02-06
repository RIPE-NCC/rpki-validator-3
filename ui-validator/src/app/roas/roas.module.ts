import {NgModule} from '@angular/core';

import {RoasListComponent} from './roas-list.component';
import {RoasService} from "./roas.service";
import {SharedModule} from "../shared/shared.module";

@NgModule({
  imports: [
    SharedModule
  ],
  declarations: [RoasListComponent],
  providers: [
    RoasService
  ]
})
export class RoasModule {
}
