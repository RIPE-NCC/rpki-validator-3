import {NgModule} from '@angular/core';

import {RoasComponent} from './roas.component';
import {RoasService} from "./roas.service";
import {SharedModule} from "../shared/shared.module";
import {ExportComponent} from "./export/export.component";

@NgModule({
  imports: [
    SharedModule
  ],
  declarations: [
    RoasComponent,
    ExportComponent
  ],
  providers: [
    RoasService
  ]
})
export class RoasModule {}
