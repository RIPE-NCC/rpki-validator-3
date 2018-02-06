import {CommonModule} from "@angular/common";
import {NgModule} from "@angular/core";
import {SearchComponent} from "./search.component";
import {TranslateModule} from "@ngx-translate/core";
import {FormsModule} from "@angular/forms";
import {NgbModule} from "@ng-bootstrap/ng-bootstrap";

@NgModule({
  imports: [
    CommonModule,
    NgbModule,
    TranslateModule,
    FormsModule],
  declarations: [
    SearchComponent
  ],
  providers: [],
  exports: [CommonModule, NgbModule, TranslateModule, SearchComponent]
})
export class SharedModule {
}
