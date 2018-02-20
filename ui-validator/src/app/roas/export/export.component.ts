import {Component, OnInit} from '@angular/core';

@Component({
  selector: 'export',
  template: `
    <h2>{{'EXPORT' | translate}}</h2>
    <p>{{'Export.DESCRIPTION' | translate}}</p>
    <a href="api/export.csv" class="btn-primary">Get CSV</a>
    <a href="api/export.json" class="btn-primary" target="_blank">Get JSON</a>
    `,
  styles: [`
    a {
      padding: 10px;
      border-radius: 5px;
    }
  `]
})
export class ExportComponent implements OnInit {

  constructor() {
  }

  ngOnInit() {
  }
}
