import { AppPage } from './app.po';
import {TrustAnchorsPage} from "./pages/TrustAnchorsPage";

describe('Trust Anchors ROAs Page', () => {
  let taPage: TrustAnchorsPage;

  beforeEach(() => {
    const page = new AppPage();
    taPage = page.navigateToTrustAnchors();
  });

  it('should display correctly Trust Anchors ROAs page', () => {
    taPage
      .expectTitleHeaderToBe('Configured Trust Anchors')
      .expectNumberOfTableRowsToBe(3)
      .expectGreenFlagInRow(1, '20932')
      .expectOrangeFlagInRow(2, '4240')
      .expectRedFlagInRow(3, '4240')
      .expectGreenMutedFlagInRow(2)
      .expectOrangeMutedFlagInRow(1)
      .expectRedMutedFlagInRow(1);
  });

  it('should open monitoring page on click on row', () => {
    taPage
      .moveMouseOverRow(1)
      .expectTooltipOnRowHover(1)
      .expectTooltipTextToBe(1, 'Click to view overview page')
      .clickOnRow(1)
      .expectToOpenMonitoringPage(3268);
  });

});
