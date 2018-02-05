import {CommonModule} from "@angular/common";
import {NgModule} from "@angular/core";
import {SearchComponent} from "./search.component";
import {TranslateModule} from "@ngx-translate/core";
import {FormsModule} from "@angular/forms";

@NgModule({
  imports: [
    CommonModule,
    TranslateModule,
    FormsModule],
  declarations: [
    SearchComponent
  ],
  providers: [],
  exports: [SearchComponent]
})
export class SharedModule {}
