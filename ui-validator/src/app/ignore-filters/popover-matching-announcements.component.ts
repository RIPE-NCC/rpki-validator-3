import {Component, Input, OnInit} from '@angular/core';
import {IBgp} from "../bgp-preview/bgp.model";
import {Router} from "@angular/router";

// TODO Ivana!!! remember to replace this component with  popover-entry
@Component({
  selector: 'popover-matching-announcements',
  template: `
    <div (mouseleave)='p.close()' (mouseover)='p.open()'>
      <div #p='ngbPopover'
            placement='bottom'
            [popoverTitle]='"DETAILS_TITLE" | translate:{value:"info"}'
            [ngbPopover]='popContent'>
        <span>{{entries.length}} {{'Whitelist.CONTEXT_ANNOUNCEMENT' | translate}}</span>
      </div>
    </div>
    <ng-template #popContent>
      <table class='table table-bordered' >
        <thead>
        <tr>
          <th>{{'PREFIX' | translate}}</th>
          <th>{{'ASN' | translate}}</th>
          <th>{{'Bgp.VALIDITY' | translate}}</th>
        </tr>
        </thead>
        <tbody>
        <tr *ngFor='let entry of entries'
            (click)='openAnnouncementPreviewDetails(entry)'
            [ngbTooltip]='"Whitelist.CLICK_LINK_TO_ANNOUNCEMENT" | translate:{value:"info"}'>
          <td>{{entry.prefix}}</td>
          <td>{{entry.asn}}</td>
          <td>{{entry.validity | translate}}</td>
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
      tbody > tr:hover {
        background-color: lightgray;
      }
  `]
})
export class PopoverMatchingAnnouncementsComponent implements OnInit {

  @Input() entries: IBgp[];

  constructor(private _router: Router) {

  }

  ngOnInit() {
  }

  openAnnouncementPreviewDetails(bgp: IBgp) {
    this._router.navigate(['/announcement-preview/'], { queryParams: { asn: bgp.asn, prefix: bgp.prefix} });
  }
}
