import { Routes } from '@angular/router';

import {TrustAnchorsComponent} from "./trust-anchors/trust-anchors.component";
import {RoasListComponent} from "./roas/roas-list.component";
import {MonitoringTaComponent} from "./monitoring-ta/monitoring-ta.component";

export const appRoutes : Routes = [
    { path: 'trust-anchors', component: TrustAnchorsComponent},
    { path: 'roas', component: RoasListComponent},
    { path: 'trust-anchors/monitor/:id', component: MonitoringTaComponent},
    { path: '', redirectTo: 'trust-anchors', pathMatch: 'full'},
    { path: '**', redirectTo: 'trust-anchors', pathMatch: 'full'}
  ]
;
