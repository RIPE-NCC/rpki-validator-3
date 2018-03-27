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
      .expectOrangeMutedFlagInRowOfSummary(4) //tooltip
      .expectRedFlagInRowOfSummary(4, '1');
  });

  it('should display correctly Validation Issues of page', () => {
    monitorTaPage
      .expectValidationIssuesSubtitleToBe('Validation Issues')
      .expectNumberOfValidationIssuesTableRowsToBe(2)
      .moveMouseOverColumn(1, 1)
      .expectPopoverOnHover(1, 1)//popover value
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
