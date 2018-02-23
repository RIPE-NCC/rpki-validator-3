import { Routes } from '@angular/router';

import {TrustAnchorsComponent} from "./trust-anchors/trust-anchors.component";
import {RoasComponent} from "./roas/roas.component";
import {MonitoringTaComponent} from "./monitoring-ta/monitoring-ta.component";
import {ErrorComponent} from "./core/error.component";

export const appRoutes : Routes = [
    { path: 'trust-anchors', component: TrustAnchorsComponent},
    { path: 'roas', component: RoasComponent},
    { path: 'trust-anchors/monitor/:id', component: MonitoringTaComponent},
    { path: '', redirectTo: 'trust-anchors', pathMatch: 'full'},
    { path: '**', component: ErrorComponent},
    { path: 'error', component: ErrorComponent}
  ]
;
