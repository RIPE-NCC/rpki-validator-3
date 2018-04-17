import {NgModule} from '@angular/core';

import {MonitoringTaComponent} from "./monitoring-ta.component";
import {SharedModule} from "../shared/shared.module";
import {ValidationDetailsComponent} from "./validation-details/validation-details.component";
import {RepositoriesComponent} from "./repositories/repositories.component";

@NgModule({
  imports: [
    SharedModule
  ],
  declarations: [
    MonitoringTaComponent,
    ValidationDetailsComponent,
    RepositoriesComponent
  ],
  providers: []
})
export class MonitoringTaModule {
}
