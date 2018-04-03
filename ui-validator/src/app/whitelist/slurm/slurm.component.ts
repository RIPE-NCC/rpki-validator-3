import {Component, EventEmitter, OnInit, Output} from '@angular/core';
import {TranslateService} from "@ngx-translate/core";

import {WhitelistService} from '../whitelist.service';
import {RpkiToastrService} from "../../core/rpki-toastr.service";

@Component({
  selector: 'slurm',
  template: `
    <div class='pt-3 pb-4'>
      <h3><strong>{{'SLURM' | translate}}</strong>
        <loading-spinner class='ml-2' [loading]='loading'></loading-spinner>
      </h3>
      <p>{{'Slurm.DESCRIPTION' | translate}}</p>
      <form>
        <div class='input-group mb-3'>
          <a href='api/slurm/download' class='btn-primary' target='_blank'>{{'Slurm.DOWNLOAD_SLURM' | translate}}</a>
          <div class='input-group-prepend ml-2'>
            <button type='button' class='btn btn-primary left-rounded'
                    [disabled]='fileName === ("Slurm.CHOOSE_FILE" | translate) || loading'
                    (click)='onSubmit()'>Upload SLURM</button>
          </div>
          <div class='custom-file'>
            <label class='custom-file-label' for='slurmFile'>{{fileName}}</label>
            <!-- #fileInput was introduced because otherwise cannot be chosen same json file twice -->
            <input type='file' #fileInput id='slurmFile' 
                   class='custom-file-input btn btn-primary'
                   (click)='fileInput.value = null' value=''
                   (change)='onFileChange($event.target.files)' accept='.json' required>
          </div>
        </div>
      </form>
    </div>
    `,
  styles: [`
    a {
      padding: 0.43rem;
      border-radius: 0.4rem;
    }
    a:hover {
      text-decoration: unset;
    }
    .input-group .btn.left-rounded {
      border-top-left-radius: 0.40rem;
      border-bottom-left-radius: 0.40rem;
    }
  `]
})
export class SlurmComponent implements OnInit {

  loading = false;
  fileName: string;
  file: File;

  @Output() uploadedSlurm: EventEmitter<any> = new EventEmitter<any>();

  constructor(private _whitelistService: WhitelistService,
              private _translateService: TranslateService,
              private _toastr: RpkiToastrService) {
  }

  ngOnInit() {
    this.initInputField();
  }

  initInputField() {
    this._translateService.get('Slurm.CHOOSE_FILE').subscribe(
      res => this.fileName = res
    );
  }

  onFileChange(files: FileList) {
    if (files.length > 0) {
      this.file = files[0];
      this.fileName = this.file.name;
    }
  }

  private prepareSave(): any {
    const input = new FormData();
    input.append('file', this.file);
    return input;
  }

  onSubmit() {
    this.loading = true;
    const formModel = this.prepareSave();
      setTimeout(() => {
       this._whitelistService.uploadSlurm(formModel).subscribe(
         res => {
           this.loading = false;
             this.initInputField();
           this.uploadedSlurm.emit();
           this._toastr.success('Slurm.SUCCESS_LOADED')
         }, error => {
           this.loading = false;
           this._toastr.error('Slurm.UPLOAD_FAILED')
         }
       );
       this.loading = false;
      }, 1000);
   }
}
