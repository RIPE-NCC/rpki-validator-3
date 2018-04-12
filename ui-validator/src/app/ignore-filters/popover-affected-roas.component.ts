import {Component, Input, OnInit} from '@angular/core';
import {IBgp} from "../bgp-preview/bgp.model";
import {Router} from "@angular/router";

// TODO Ivana!!! remember to replace this component with  popover-entry
@Component({
  selector: 'popover-affected-roas',
  template: `
    <div (mouseleave)='p.close()' (mouseover)='p.open()'>
      <div #p='ngbPopover'
            placement='bottom'
            [popoverTitle]='"DETAILS_TITLE" | translate:{value:"info"}'
            [ngbPopover]='popContent'>
        <span>{{entries.length}} {{'IgnoreFilters.NUMBER_AFFECTED_ROAS' | translate}}</span>
      </div>
    </div>
    <ng-template #popContent>
      <table class='table table-bordered' >
        <thead>
        <tr>
          <th>{{'PREFIX' | translate}}</th>
          <th>{{'ASN' | translate}}</th>
        </tr>
        </thead>
        <tbody>
        <tr *ngFor='let entry of entries'>
          <td>{{entry.prefix}}</td>
          <td>{{entry.asn}}</td>
        </tr>
        </tbody>
      </table>
    </ng-template>
  `,
  styles: [`
      ::ng-deep .popover {
        margin-top: 2px;
        max-width: inherit;
        width: 25rem;
      }
  `]
})
export class PopoverAffectedRoasComponent implements OnInit {

  @Input() entries: IBgp[];

  constructor(private _router: Router) {

  }

  ngOnInit() {
  }
}
