import {browser, by, element} from 'protractor';

export class MonitorTaPage {

  constructor() {
    browser.get('/trust-anchors/monitor/3268');
  }

  expectTitleHeaderToBe(title: string) {
    expect(element(by.css('app-root .page-header h1')).getText()).toEqual(title);
    return this;
  }

  expectSummerySubtitleToBe(title: string) {
    expect(element(by.css('#summary h3')).getText()).toEqual(title);
    return this;
  }

  expectNumberOfSummaryTableRowsToBe(rows: number) {
    expect(element.all(by.css('#summary table tbody tr')).count()).toBe(rows);
    return this;
  }

  expectCanValidatTrustAnchorToBe(value: string) {
    expect(element(by.css('#summary table tr:nth-child(1) td:nth-child(1)')).getText()).toEqual('Trust Anchor could be validated');
    // green flag
    expect(element(by.css('#summary table tr:nth-child(1) .alert-success')).getText()).toEqual(value);
    return this;
  }

  expectLastCompletedValidationToBe(date: string) {
    expect(element(by.css('#summary table tr:nth-child(2) td:nth-child(1)')).getText()).toEqual('Last completed validation');
    expect(element(by.css('#summary table tr:nth-child(2) td:nth-child(2)')).getText()).toEqual(date);
    return this;
  }

  expectProcessedItemsToHave() {
    expect(element(by.css('#summary table tr:nth-child(3) td:nth-child(1)')).getText()).toEqual('Processed Items');
    return this;
  }

  expectRepositoriesToHave() {
    expect(element(by.css('#summary table tr:nth-child(4) td:nth-child(1)')).getText()).toEqual('Repositories');
    return this;
  }

  expectGreenFlagInRowOfSummary(row: number, content: string) {
    expect(element(by.css(`#summary table tbody tr:nth-child(${row}) flag[ng-reflect-color=green] .alert`)).getText()).toBe(content);
    return this;
  }

  expectRedFlagInRowOfSummary(row: number, content: string) {
    expect(element(by.css(`#summary table tbody tr:nth-child(${row}) flag[ng-reflect-color=red] .alert`)).getText()).toBe(content);
    return this;
  }

  expectOrangeMutedFlagInRowOfSummary(row: number) {
    expect(element(by.css(`#summary table tbody tr:nth-child(${row}) flag[ng-reflect-color="orange"] .alert-warning-muted`)).getText()).toBe('0');
    return this;
  }

  expectRedMutedFlagInRowOfSummary(row: number) {
    expect(element(by.css(`#summary table tbody tr:nth-child(${row}) flag[ng-reflect-color="red"] .alert-danger-muted`)).getText()).toBe('0');
    return this;
  }

  moveMouseOverColumnInSummaryTable(row: number, column: number, flag: number) {
    browser.actions().mouseMove(element(by.css(`#summary table tbody tr:nth-child(${row}) td:nth-child(${column}) flag:nth-child(${flag})`))).perform();
    return this;
  }

  expectTooltipOnRowHover(row: number, column: number, flag: number) {
    expect(element(by.css(`#summary table tbody tr:nth-child(${row}) td:nth-child(${column}) flag:nth-child(${flag})[ng-reflect-ngb-tooltip]`)).isDisplayed()).toBe(true);
    return this;
  }

  expectTooltipTextToBe(row: number, column: number, flag: number, tooltipText: string) {
    expect(element(by.css(`#summary table tbody tr:nth-child(${row}) td:nth-child(${column}) flag:nth-child(${flag})`)).getAttribute('ng-reflect-ngb-tooltip')).toBe(tooltipText);
    return this;
  }

  // VALIDATION ISSUES
  expectValidationIssuesSubtitleToBe(title: string) {
    expect(element(by.css('validation-details h3')).getText()).toEqual(title);
    return this;
  }

  expectNumberOfValidationIssuesTableRowsToBe(rows: number) {
    expect(element.all(by.css('validation-details table tbody tr')).count()).toBe(rows);
    return this;
  }

  expectPopoverToHave(row: number, column: number, expectToSee: boolean) {
    expect(element(by.css(`validation-details table tbody tr:nth-child(${row}) td:nth-child(${column}) ngb-popover-window`)).isPresent()).toBe(expectToSee);
    return this;
  }

  expectPopoverOnHover(row: number, column: number) {
    expect(element(by.css(`validation-details table tbody tr:nth-child(${row}) td:nth-child(${column}) ngb-popover-window`)).isDisplayed()).toBe(true);
    return this;
  }

  expectPopoverLinkToBe(row: number, column: number, link: string) {
    expect(element(by.css(`validation-details table tbody tr:nth-child(${row}) td:nth-child(${column}) ngb-popover-window`)).getText()).toContain(link);
    return this;
  }

  moveMouseOverColumnInValidationDetailsTable(row: number, column: number) {
    browser.actions().mouseMove(element(by.css(`validation-details table tbody tr:nth-child(${row}) td:nth-child(${column}) img`))).perform();
    return this;
  }

  expectErrorFlagInRowOfValidationDetails(row: number) {
    expect(element(by.css(`validation-details table tbody tr:nth-child(${row}) flag .alert-danger`)).getText()).toBe('ERROR');
    return this;
  }

  expectWarningFlagInRowOfValidationDetails(row: number) {
    expect(element(by.css(`validation-details table tbody tr:nth-child(${row}) flag .alert-warning`)).getText()).toBe('WARNING');
    return this;
  }

  // REPOSITORIES
  expectRepositoriesSubtitleToBe(title: string) {
    expect(element(by.css('repositories h3')).getText()).toEqual(title);
    return this;
  }

  expectNumberOfRepositoriesTableRowsToBe(rows: number) {
    expect(element.all(by.css('repositories table tbody tr')).count()).toBe(rows);
    return this;
  }

  expectSuccessFlagInRowOfRepositories(row: number) {
    expect(element(by.css(`repositories table tbody tr:nth-child(${row}) flag .alert-success`)).getText()).toBe('DOWNLOADED');
    return this;
  }

  expectErrorFlagInRowOfRepositories(row: number) {
    expect(element(by.css(`repositories table tbody tr:nth-child(${row}) flag .alert-danger`)).getText()).toBe('FAILED');
    return this;
  }
}
