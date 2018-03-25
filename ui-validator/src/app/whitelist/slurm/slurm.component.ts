import {Component, OnInit} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';

import {WhitelistService} from '../whitelist.service';

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
                    [disabled]='fileName === ("Slurm.CHOOSE_FILE" | translate)'
                    (click)='onSubmit()'>Upload SLURM</button>
          </div>
          <div class='custom-file'>
            <input type='file' class='custom-file-input btn btn-primary' id='slurmFile' (change)='onFileChange($event.target.files)' required>
            <label class='custom-file-label' for='slurmFile'>{{ fileName }}</label>
          </div>
        </div>
        <em *ngIf='errorUploading'>{{'Slurm.UPLOAD_FAILED' | translate}}</em>
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
  errorUploading: boolean = false;

  constructor(private _whitelistService: WhitelistService, private translate: TranslateService) {
  }

  ngOnInit() {
    this.initInputField();
  }

  initInputField() {
    this.translate.get('Slurm.CHOOSE_FILE').subscribe((res: string) => {
      this.fileName = res;
    });
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
     const formModel = this.prepareSave();
     this.loading = true;
     setTimeout(() => {
       this._whitelistService.uploadSlurm(formModel).subscribe(
         res => {
           this.errorUploading = false;
           this.initInputField();
         }, error => this.errorUploading = true
       );
       this.loading = false;
     }, 1000);
   }
}
