import {ErrorHandler, NgModule} from '@angular/core';
import {RouterModule} from '@angular/router';
import {NgbModule} from "@ng-bootstrap/ng-bootstrap";
import {TranslateModule} from "@ngx-translate/core";

import {NavComponent} from "./nav/nav.component";
import {FooterComponent} from "./footer/footer.component";
import {TrustAnchorsService} from "./trust-anchors.service";
import {ErrorComponent} from "./error.component";
import {ApplicationErrorHandlerService} from "./app-error-handler.service";
import {MonitoringTaDataStore} from "./monitoring-ta-data.store";
import {BgpService} from "./bgp.service";

@NgModule({
  imports: [
    RouterModule,
    NgbModule.forRoot(),
    TranslateModule
  ],
  declarations: [
    NavComponent,
    FooterComponent,
    ErrorComponent
  ],
  providers: [
    TrustAnchorsService,
    BgpService,
    {provide: ErrorHandler, useClass: ApplicationErrorHandlerService},
    MonitoringTaDataStore
  ],
  exports: [
    NavComponent,
    FooterComponent,
    ErrorComponent
  ]
})
export class CoreModule {
}
