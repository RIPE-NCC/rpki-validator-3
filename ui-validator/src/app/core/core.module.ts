import {NgModule} from '@angular/core';
import {RouterModule} from '@angular/router';
import {NgbModule} from "@ng-bootstrap/ng-bootstrap";
import {TranslateModule} from "@ngx-translate/core";

import {NavComponent} from "./nav/nav.component";
import {FooterComponent} from "./footer/footer.component";
import {TrustAnchorsService} from "./trust-anchors.service";

@NgModule({
  imports: [
    RouterModule,
    NgbModule.forRoot(),
    TranslateModule
  ],
  declarations: [
    NavComponent,
    FooterComponent
  ],
  providers: [
    TrustAnchorsService
  ],
  exports: [
    NavComponent,
    FooterComponent
  ]
})
export class CoreModule {
};
