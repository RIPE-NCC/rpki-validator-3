import {Component, OnInit} from '@angular/core';

@Component({
  selector: 'export',
  template: `
    <div class="pt-3 pb-4">
      <h3><strong>{{'EXPORT' | translate}}</strong></h3>
      <p>{{'Export.DESCRIPTION' | translate}}</p>
      <a href="api/export.csv" class="btn-primary">Get CSV</a>
      <a href="api/export.json" class="btn-primary" target="_blank">Get JSON</a>
      <a href="api/export-extended.json" class="btn-primary" target="_blank">Get JSON (extended)</a>
    </div>
    `,
  styles: [`
    a {
      padding: 10px;
      border-radius: 5px;
    }
    a:hover {
      text-decoration: unset;
    }
  `]
})
export class ExportComponent implements OnInit {

  constructor() {
  }

  ngOnInit() {
  }
}
