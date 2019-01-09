import {AppPage} from './app.po';
import {MonitorTaPage} from "./pages/MonitorTaPage";

describe('Monitor TA Page', () => {
  let monitorTaPage: MonitorTaPage;

  beforeEach(() => {
    const page = new AppPage();
    monitorTaPage = page.navigateToMonitorTa();
  });

  it('should display correctly Monitor TA page', () => {
    monitorTaPage
      .expectTitleHeaderToBe('Overview for RIPE NCC RPKI Root');
  });

  it('should display correctly Summery of page', () => {
    monitorTaPage
    .expectSummerySubtitleToBe('Summary')
    .expectNumberOfSummaryTableRowsToBe(4)
    .expectCanValidatTrustAnchorToBe('Yes')
    .expectLastCompletedValidationToBe('2018-02-25 04:32:31')
    .expectProcessedItemsToHave()
    .expectGreenFlagInRowOfSummary(3, '20932')
    .expectOrangeMutedFlagInRowOfSummary(3)
    .expectRedMutedFlagInRowOfSummary(3)
    .expectRepositoriesToHave()
    .expectGreenFlagInRowOfSummary(4, '91')
    .expectOrangeMutedFlagInRowOfSummary(4)
    .expectRedFlagInRowOfSummary(4, '1')
  });

  it('should display tooltips on hover over flags in summary table', () => {
    monitorTaPage
      .moveMouseOverColumnInSummaryTable(3, 2, 1)
      .expectTooltipOnRowHover(3,2, 1)
      .expectTooltipTextToBe(3, 2, 1, 'valid objects')
      .moveMouseOverColumnInSummaryTable(4, 2, 1)
      .expectTooltipOnRowHover(4, 2, 1)
      .expectTooltipTextToBe(4, 2, 1, 'downloaded repositories')
      .moveMouseOverColumnInSummaryTable(3, 2, 2)
      .expectTooltipOnRowHover(3, 2, 2)
      .expectTooltipTextToBe(3, 2, 2, 'objects with warnings')
      .moveMouseOverColumnInSummaryTable(4, 2, 2)
      .expectTooltipOnRowHover(4, 2, 2)
      .expectTooltipTextToBe(4, 2, 2, 'pending repositories')
      .moveMouseOverColumnInSummaryTable(3, 2, 3)
      .expectTooltipOnRowHover(3, 2, 3)
      .expectTooltipTextToBe(3, 2, 3, 'invalid objects')
      .moveMouseOverColumnInSummaryTable(4, 2, 3)
      .expectTooltipOnRowHover(4, 2, 3)
      .expectTooltipTextToBe(4, 2, 3, 'unreachable repositories');
  });

  it('should display correctly Validation Issues of page', () => {
    monitorTaPage
      .expectValidationIssuesSubtitleToBe('Validation Issues')
      .expectNumberOfValidationIssuesTableRowsToBe(2)
      .moveMouseOverColumnInValidationDetailsTable(1, 1)
      .expectPopoverOnHover(1, 1)
      .expectPopoverLinkToBe(1, 1, 'rsync://ca.rg.net/rpki/RGnet/2GliShrzqgtMquuCAoH0Re5u_vQ.gbr')
      .expectErrorFlagInRowOfValidationDetails(1)
      .expectWarningFlagInRowOfValidationDetails(2);
  });

  it('should display correctly Repositories of page', () => {
    monitorTaPage
      .expectRepositoriesSubtitleToBe('Repositories')
      .expectNumberOfRepositoriesTableRowsToBe(2)
      .expectSuccessFlagInRowOfRepositories(1)
      .expectErrorFlagInRowOfRepositories(2);
  });
})
