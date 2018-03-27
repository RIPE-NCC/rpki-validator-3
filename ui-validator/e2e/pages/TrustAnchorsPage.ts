import {browser, by, element} from 'protractor';

export class TrustAnchorsPage {

  constructor() {
    browser.get('/trust-anchors');
  }

  changePaginationSizeTo(size: number) {
    element(by.cssContainingText('page-size-select select option', size.toString())).click();
    return this;
  }

  expectTitleHeaderToBe(title: string) {
    expect(element(by.css('app-root .page-header h1')).getText()).toEqual(title);
    return this;
  }

  expectNumberOfTableRowsToBe(rows: number) {
    expect(element.all(by.css('table tbody tr')).count()).toBe(rows);
    return this;
  }

  expectGreenFlagInRow(row: number, content: string) {
    this.expectColorFlagInRow('green', row, content);
    return this;
  }

  expectOrangeFlagInRow(row: number, content: string) {
    this.expectColorFlagInRow('orange', row, content);
    return this;
  }

  expectRedFlagInRow(row: number, content: string) {
    this.expectColorFlagInRow('red', row, content);
    return this;
  }

  expectColorFlagInRow(color: string, row: number, content: string) {
    expect(element(by.css(`table tbody tr:nth-child(${row}) flag[ng-reflect-color="${color}"] .alert`)).getText()).toBe(content);
    return this;
  }

  expectGreenMutedFlagInRow(row: number) {
    expect(element(by.css(`table tbody tr:nth-child(${row}) flag[ng-reflect-color="green"] .alert-success-muted`)).getText()).toBe('0');
    return this;
  }

  expectOrangeMutedFlagInRow(row: number) {
    expect(element(by.css(`table tbody tr:nth-child(${row}) flag[ng-reflect-color="orange"] .alert-warning-muted`)).getText()).toBe('0');
    return this;
  }

  expectRedMutedFlagInRow(row: number) {
    expect(element(by.css(`table tbody tr:nth-child(${row}) flag[ng-reflect-color="red"] .alert-danger-muted`)).getText()).toBe('0');
    return this;
  }

  moveMouseOverRow(row: number) {
    browser.actions().mouseMove(element(by.css(`table tbody tr:nth-child(${row})`))).perform();
    return this;
  }

  expectTooltipOnRowHover(row: number) {
    expect(element(by.css(`table tbody tr:nth-child(${row})[ng-reflect-ngb-tooltip]`)).isDisplayed()).toBe(true);
    return this;
  }

  expectTooltipTextToBe(row: number, tooltipText: string) {
    expect(element(by.css(`table tbody tr:nth-child(${row})`)).getAttribute('ng-reflect-ngb-tooltip')).toBe(tooltipText);
    return this;
  }

  clickOnRow(row: number) {
    element(by.css(`table tbody tr:nth-child(${row})`)).click();
    return this;
  }

  expectToOpenMonitoringPage(taId: number) {
    expect(browser.getCurrentUrl()).toContain(`/trust-anchors/monitor/${taId}`);
    return this;
  }
}
