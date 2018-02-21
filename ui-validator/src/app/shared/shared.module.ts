import {CommonModule} from "@angular/common";
import {NgModule} from "@angular/core";
import {NgbModule} from "@ng-bootstrap/ng-bootstrap";
import {TranslateModule} from "@ngx-translate/core";
import {FormsModule} from "@angular/forms";

import {SearchComponent} from "./search.component";
import {PageTitleComponent} from "./page-title/page-title.component";
import {FlagComponent} from './flag/flag.component';
import {SortableColumnComponent} from "./sortable-table/sortable-column.component";
import {SortService} from "./sortable-table/sort.service";
import {SortableTableDirective} from "./sortable-table/sortable-table.directive";
import {LoadingSpinnerComponent} from "./loading-spinner.component";
import {PageSizeSelectComponent} from "./page-size-select.component";
import {PageTextuallyStatusComponent} from "./page-textually-status.component";
import {ManagingTable} from "./managing-table";

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
    SortableColumnComponent,
    SortableTableDirective,
    LoadingSpinnerComponent,
    PageSizeSelectComponent,
    PageTextuallyStatusComponent
  ],
  providers: [SortService],
  exports: [
    CommonModule,
    NgbModule,
    TranslateModule,
    SearchComponent,
    PageTitleComponent,
    FlagComponent,
    SortableColumnComponent,
    SortableTableDirective,
    LoadingSpinnerComponent,
    PageSizeSelectComponent,
    PageTextuallyStatusComponent
  ]
})
export class SharedModule {
}
