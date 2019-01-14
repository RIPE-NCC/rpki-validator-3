import {AppPage} from './app.po';
import {BgpPage} from "./pages/BgpPage";

describe('Bgp Preview Page', () => {
  let bgpPage: BgpPage;
  const page = new AppPage();

  beforeEach(() => {
    bgpPage = page.navigateToBgp('')
  });

  it('should display correctly BGP Preview page', () => {
    bgpPage
      .expectTitleHeaderToBe('BGP Preview')
      .expectPaginationSizeToBe(10);
  });

  it('should search directly when using query param', () => {
    page.navigateToBgp('AS9737')
      .expectNumberOfTableRowsToBe(3)
      .expectShowingEntriesToBe('Showing 1 to 3 of 3 entries');
  });


  it('should navigate to Announcement Page on click on row', () => {
    bgpPage
      .moveMouseOverRow(1)
      .expectTooltipOnRowHover(1)
      .expectTooltipTextToBe(1, 'Click to see announcement prev')
      .clickOnRow(1)
      .expectToOpenAnnouncementPage('AS56203','1.0.4.0/22');
  });
});
