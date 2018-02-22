import {Component, Input} from "@angular/core";

@Component({
  selector: 'page-title',
  template: `
    <div class='page-header'>
      <h1><strong>{{pageTitle | translate}} {{specificValue}}</strong></h1>
    </div>
  `,
  styles: [`
    .page-header {
      margin-bottom: 17px;
      margin-top: 17px;
      border-bottom: 1px solid #ddd;
      color: #404040;
    }
  `]
})
export class PageTitleComponent {
  @Input() pageTitle: string;
  @Input() specificValue?: string;

  constructor(){}
}
