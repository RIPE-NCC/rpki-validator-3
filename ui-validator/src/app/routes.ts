import {Routes} from '@angular/router';

import {TrustAnchorsComponent} from "./trust-anchors/trust-anchors.component";
import {RoasComponent} from "./roas/roas.component";
import {MonitoringTaComponent} from "./monitoring-ta/monitoring-ta.component";
import {ErrorComponent} from "./core/error.component";
import {IgnoreFiltersComponent} from "./ignore-filters/ignore-filters.component";
import {BgpPreviewComponent} from "./bgp-preview/bgp-preview.component";
import {AnnouncementPreviewComponent} from "./announcement-preview/announcement-preview.component";

export const appRoutes: Routes = [
    {path: 'trust-anchors', component: TrustAnchorsComponent},
    {path: 'roas', component: RoasComponent},
    {path: 'trust-anchors/monitor/:id', component: MonitoringTaComponent},
    {path: 'filters', component: IgnoreFiltersComponent},
    {path: 'bgp-preview', component: BgpPreviewComponent},
    {path: 'announcement-preview', component: AnnouncementPreviewComponent, pathMatch: 'prefix'},
    {path: '', redirectTo: 'trust-anchors', pathMatch: 'full'},
    {path: '**', component: ErrorComponent},
    {path: 'error', component: ErrorComponent}
  ]
;
