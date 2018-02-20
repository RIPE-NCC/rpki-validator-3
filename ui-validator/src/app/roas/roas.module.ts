import {NgModule} from '@angular/core';

import {RoasListComponent} from './roas-list.component';
import {RoasService} from "./roas.service";
import {SharedModule} from "../shared/shared.module";
import {ExportComponent} from "./export/export.component";

@NgModule({
  imports: [
    SharedModule
  ],
  declarations: [
    RoasListComponent,
    ExportComponent
  ],
  providers: [
    RoasService
  ]
})
export class RoasModule {}
