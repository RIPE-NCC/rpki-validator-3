import {NgModule} from '@angular/core';
import {CommonModule} from '@angular/common';
import {RouterModule} from '@angular/router';
import {NavComponent} from "./nav/nav.component";
import {NgbModule} from "@ng-bootstrap/ng-bootstrap";
import {TranslateModule} from "@ngx-translate/core";
import {FooterComponent} from "./footer/footer.component";

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
  // providers: [ UserRepositoryService ]
})
export class CoreModule {
};
