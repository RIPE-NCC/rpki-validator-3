import {NgModule} from '@angular/core';

import {TrustAnchorsComponent} from './trust-anchors.component';
import {MonitoringTaComponent} from "../monitoring-ta/monitoring-ta.component";
import {SharedModule} from "../shared/shared.module";

@NgModule({
  imports: [
    SharedModule
  ],
  declarations: [TrustAnchorsComponent, MonitoringTaComponent],
  providers: []
})
export class TrustAnchorsModule {
}
