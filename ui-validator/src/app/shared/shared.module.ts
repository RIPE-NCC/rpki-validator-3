import {CommonModule} from "@angular/common";
import {NgModule} from "@angular/core";
import {NgbModule} from "@ng-bootstrap/ng-bootstrap";
import {TranslateModule} from "@ngx-translate/core";
import {FormsModule} from "@angular/forms";

import {SearchComponent} from "./search.component";
import {PageTitleComponent} from "./page-title.component";
import {FlagComponent} from './flag/flag.component';

@NgModule({
  imports: [
    CommonModule,
    NgbModule.forRoot(),
    TranslateModule.forRoot(),
    FormsModule
  ],
  declarations: [
    SearchComponent,
    PageTitleComponent,
    FlagComponent
  ],
  providers: [],
  exports: [
    CommonModule,
    NgbModule,
    TranslateModule,
    SearchComponent,
    PageTitleComponent,
    FlagComponent
  ]
})
export class SharedModule {
}
