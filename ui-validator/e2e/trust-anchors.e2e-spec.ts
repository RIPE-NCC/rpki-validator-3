import { AppPage } from './app.po';
import {TrustAnchorsPage} from "./pages/TrustAnchorsPage";

describe('ui-validator App', () => {
  let taPage: TrustAnchorsPage;

  beforeEach(() => {
    const page = new AppPage();
    taPage = page.navigateToTrustAnchors();
  });

  it('should display correctly Trust Anchors ROAs page', () => {
    taPage
      .expectTitleHeaderToBe('Configured Trust Anchors')
      .expectNumberOfTableRowsToBe(3)
      .expectGreenFlagInRow(0, '20932')
      .expectOrangeFlagInRow(1, '4240')
      .expectRedFlagInRow(2, '4240')
      .expectGreenMutedFlagInRow(1)
      .expectOrangeMutedFlagInRow(0)
      .expectRedMutedFlagInRow(0);
  });

  it('should open monitoring page on click on row', () => {
    taPage
      .moveMouseOverRow(0)
      .expectTooltipOnRowHover(0)
      .expectTooltipTextToBe(0, 'Click to view overview page')
      .clickOnRow(0)
      .expectToOpenMonitoringPage(3268);
  });

});
