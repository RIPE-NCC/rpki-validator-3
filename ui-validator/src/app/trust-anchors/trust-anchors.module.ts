import {NgModule} from '@angular/core';

import {TrustAnchorsComponent} from './trust-anchors.component';
import {MonitoringTaComponent} from "../monitoring-ta/monitoring-ta.component";
import {SharedModule} from "../shared/shared.module";
import {ValidationDetailsComponent} from "../monitoring-ta/validation-details/validation-details.component";

@NgModule({
  imports: [
    SharedModule
  ],
  declarations: [
    TrustAnchorsComponent,
    MonitoringTaComponent,
    ValidationDetailsComponent
  ],
  providers: []
})
export class TrustAnchorsModule {
}
