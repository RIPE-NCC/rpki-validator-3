import {NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';
import {RouterModule} from '@angular/router';
import {NgbModule} from "@ng-bootstrap/ng-bootstrap";

import {NavComponent} from "./nav/nav.component";
import {TranslateModule} from "@ngx-translate/core";
import {FooterComponent} from "./footer/footer.component";
import {HttpClientModule} from "@angular/common/http";

@NgModule({
  imports: [
    CommonModule,
    RouterModule,
    NgbModule.forRoot(),
    TranslateModule
  ],
  declarations: [NavComponent, FooterComponent],
  exports: [NavComponent, FooterComponent],
  providers: []
})
export class CoreModule {
};
