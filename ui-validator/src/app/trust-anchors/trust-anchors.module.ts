import {NgModule} from '@angular/core';

import {TrustAnchorsComponent} from './trust-anchors.component';
import {TrustAnchorsService} from "./trust-anchors.service";
import {MonitoringTaComponent} from "../monitoring-ta/monitoring-ta.component";
import {SharedModule} from "../shared/shared.module";

@NgModule({
  imports: [
    SharedModule
  ],
  declarations: [TrustAnchorsComponent, MonitoringTaComponent],
  providers: [TrustAnchorsService]
})
export class TrustAnchorsModule {
}
