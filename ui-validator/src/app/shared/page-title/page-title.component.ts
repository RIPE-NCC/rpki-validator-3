import {Component, Input} from "@angular/core";

@Component({
  selector: 'page-title',
  templateUrl: './page-title.component.html',
  styleUrls: ['./page-title.component.scss']
})
export class PageTitleComponent {
  @Input() pageTitle: string;
  @Input() specificValue?: string;

  constructor(){
  }
}
