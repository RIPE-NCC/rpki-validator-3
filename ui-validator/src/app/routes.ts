import { Routes } from '@angular/router';

import {TrustAnchorsComponent} from "./trust-anchors/trust-anchors.component";
import {HomeComponent} from "./home/home.component";
import {RoasListComponent} from "./roas/roas-list.component";
import {MonitoringTaComponent} from "./monitoring-ta/monitoring-ta.component";

export const appRoutes : Routes = [
  { path: 'home', component: HomeComponent},
    { path: 'trust-anchors', component: TrustAnchorsComponent},
    { path: 'list-roas', component: RoasListComponent},
    { path: 'trust-anchor-monitor/:id', component: MonitoringTaComponent},
    { path: '', redirectTo: 'home', pathMatch: 'full'},
    { path: '**', redirectTo: 'home', pathMatch: 'full'}
  ]
;
