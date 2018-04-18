import {AppPage} from './app.po';
import {WhitelistPage} from './pages/WhitelistPage';

fdescribe('Whitelist Page', () => {
  let whitelistPage: WhitelistPage;

  beforeEach(() => {
    const page = new AppPage();
    whitelistPage = page.navigateToWhitelist();
  });

  describe('add entry to whitelist', () => {

    it('should display correctly Whitelist title', () => {
      whitelistPage
        .expectTitleHeaderToBe('Whitelist');
    });

    it('should show validation error if prefix and asn are empty', () => {
      whitelistPage
        .clearEntryForm()
        .expectPrefixAndAsnValidationMessage();
    });

    it('should not add a entry to whitelist with invalid prefix', () => {
      whitelistPage
        .addWhitelistEntry('NotValid', 'AS1234')
        .expectPrefixValidationMessage()
    });

    it('should not add a entry to whitelist with invalid asn', () => {
      whitelistPage
        .addWhitelistEntry('1.0.128.0/20', 'NotValid')
        .expectAsnValidationMessage()
    });

    it('should add a entry without and with comment', () => {
      whitelistPage
        .addWhitelistEntry('1.0.128.0/20', 'AS1234')
        .expectEntryAdded()
        .clearEntryForm()
        .addWhitelistEntry('1.0.128.0/20', 'AS1234', 13)
        .expectEntryAdded()
        .clearEntryForm()
        .addWhitelistEntry('1.0.128.0/20', 'AS1234', 12,'e2e test comment')
        .expectEntryAdded();
    });
  });

  describe('current whitelist entry', () => {
    it('should show entry on table', () => {
      whitelistPage
        .expectNumberOfTableRowsToBe(2)
        .expectRowColumnToBe(1, 'Prefix', '1.0.128.0/20')
        .expectRowColumnToBe(1, 'ASN', '1234')
        .expectRowColumnToBe(2, 'Prefix', '2.0.128.0/20')
        .expectRowColumnToBe(2, 'ASN', '3215')
        .expectRowColumnToBe(2, 'Maximum prefix length', '20')
    });

    it('should delete entry on table', () => {
      whitelistPage
        .expectNumberOfTableRowsToBe(2)
        .deleteEntry(2)
    });

    it('should show popover on mouse over announcement > 0', () => {
      whitelistPage
        .expectNumberOfAnnouncments(1, 4, '0 announcement(s)')
        .expectPopoverToHave(1, 4, false)
        .expectNumberOfAnnouncments(1, 5, '1 announcement(s)')
        .expectPopoverToHave(1, 5, true)
        .moveMouseOverColumn(1, 5)
        .expectPopoverOnHover(1, 5)
        .moveMouseOverRowInPopover(1, 5)
        .expectTooltipTextToBe(1, 5, 'Click link to see details')
        .expectNumberOfAnnouncments(2, 4, '1 announcement(s)')
        .expectPopoverToHave(2, 4, true)
        .expectNumberOfAnnouncments(2, 5, '0 announcement(s)')
        .expectPopoverToHave(2, 5, false)
        .moveMouseOverColumn(2, 4)
        .expectPopoverOnHover(2, 4)
        .moveMouseOverRowInPopover(2, 4)
        .expectTooltipTextToBe(2, 4, 'Click link to see details');
    });

    it('should navigate to selected announcement on click in popover', () => {
      whitelistPage
        .moveMouseOverColumn(1, 5)
        .expectPopoverOnHover(1, 5)
        .clickOnRowInPopoverOfSpecifiedCell(1, 5)
        .expectToOpenAnnouncementPage('AS56203', '1.0.6.0/24');
    });
  });
});
