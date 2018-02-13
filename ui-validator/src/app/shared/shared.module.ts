import {CommonModule} from "@angular/common";
import {NgModule} from "@angular/core";
import {NgbModule} from "@ng-bootstrap/ng-bootstrap";
import {TranslateModule} from "@ngx-translate/core";
import {FormsModule} from "@angular/forms";

import {SearchComponent} from "./search.component";
import {PageTitleComponent} from "./page-title.component";
import {FlagComponent} from './flag/flag.component';
import {SortableColumnComponent} from "./sortable-column/sortable-column.component";
import {SortService} from "./sortable-column/sort.service";

@NgModule({
  imports: [
    CommonModule,
    NgbModule.forRoot(),
    TranslateModule,
    FormsModule
  ],
  declarations: [
    SearchComponent,
    PageTitleComponent,
    FlagComponent,
    SortableColumnComponent
  ],
  providers: [SortService],
  exports: [
    CommonModule,
    NgbModule,
    TranslateModule,
    SearchComponent,
    PageTitleComponent,
    FlagComponent,
    SortableColumnComponent
  ]
})
export class SharedModule {
}
