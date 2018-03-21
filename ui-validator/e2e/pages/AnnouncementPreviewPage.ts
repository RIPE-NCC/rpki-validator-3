import {browser, by, element, protractor} from 'protractor';

export class AnnouncementPreviewPage {

  constructor(asn: string, prefix: string) {
    let uri = '/announcement-preview';
    if (asn && prefix) {
      uri += '?asn=$1&prefix=$2';
      uri.replace('$1', asn);
      uri.replace('$2', prefix);
    }
    browser.get(uri);
  }

  expectTitleHeaderToBe(title: string) {
    expect(element(by.css('app-root .page-header h1')).getText()).toEqual(title);
    return this;
  }

  expectASNInPanelToBe(value: string) {
    expect(element(by.css('.announcement-panel span:nth-child(1)')).getText()).toContain(value);
    return this;
  }

  expectPrefixInPanelToBe(value: string) {
    expect(element(by.css('.announcement-panel span:nth-child(2)')).getText()).toContain(value);
    return this;
  }

  expectGreenFlagInPanel(value: string) {
    expect(element(by.css('.announcement-panel .alert-success')).getText()).toContain(value);
    return this;
  }

  expectSubtitleToBe(subtitle: string) {
    expect(element(by.css('h3')).getText()).toEqual(subtitle);
    return this;
  }

  expectNumberOfTableRowsToBe(rows: number) {
    expect(element.all(by.css('table tbody tr')).count()).toBe(rows);
    return this;
  }

  expectShowModalWindow() {
    expect(element(by.css('ngb-modal-window')).isPresent()).toBe(true);
    return this;
  }

  expectModalWindowMessage(title: string) {
    expect(element(by.css('ngb-modal-window h4')).getText()).toEqual(title);
    return this;
  }

  expectModalWindowToContainInputField(fieldName: string) {
    expect(element(by.css(`ngb-modal-window input[name="${fieldName}"]`)).isPresent()).toBe(true);
    return this;
  }

  expectShowButtonOnModalToBe(enabled: boolean) {
    expect(element(by.css('ngb-modal-window .btn-primary')).isEnabled()).toBe(enabled);
    return this;
  }

  setValueToInput(fieldName: string, value: string) {
    element(by.css(`ngb-modal-window input[name="${fieldName}"]`)).sendKeys(value);
    return this;
  }

  removeValueFromInput(fieldName: string) {
    element(by.css(`ngb-modal-window input[name="${fieldName}"]`)).sendKeys(protractor.Key.BACK_SPACE);
    element(by.css(`ngb-modal-window input[name="${fieldName}"]`)).clear();
    return this;
  }

  goToAnnouncementPage() {
    element(by.css('ngb-modal-window .btn-primary')).click();
    return this;
  }

  dismissModalWindow() {
    browser.actions().mouseMove(element(by.css('ngb-modal-window')),{x: 10, y: 10}).click();
    return this;
  }
}
