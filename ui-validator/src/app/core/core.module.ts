import {ErrorHandler, NgModule} from '@angular/core';
import {RouterModule} from '@angular/router';
import {NgbModule} from "@ng-bootstrap/ng-bootstrap";
import {TranslateModule} from "@ngx-translate/core";

import {NavComponent} from "./nav/nav.component";
import {FooterComponent} from "./footer/footer.component";
import {TrustAnchorsService} from "./trust-anchors.service";
import {ErrorComponent} from "./error.component";
import {ApplicationErrorHandler} from "./app-error-handler.service";
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
    {provide: ErrorHandler, useClass: ApplicationErrorHandler}
  ],
  exports: [
    NavComponent,
    FooterComponent,
    ErrorComponent
  ]
})
export class CoreModule {
}
